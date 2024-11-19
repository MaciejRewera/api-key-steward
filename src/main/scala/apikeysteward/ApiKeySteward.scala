package apikeysteward

import apikeysteward.config.{AppConfig, DatabaseConnectionExecutionContextConfig}
import apikeysteward.generators._
import apikeysteward.repositories.db._
import apikeysteward.repositories._
import apikeysteward.routes._
import apikeysteward.routes.auth._
import apikeysteward.routes.auth.model.JwtCustom
import apikeysteward.services._
import apikeysteward.utils.Logging
import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import org.http4s.HttpApp
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.CORS
import pureconfig.ConfigSource

import java.time.Clock
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object ApiKeySteward extends IOApp.Simple with Logging {

  private implicit val clock: Clock = Clock.systemUTC

  override def run: IO[Unit] = {

    val resources = for {
      config <- Resource
        .eval(ConfigSource.default.load[AppConfig] match {
          case Left(failures) => IO.raiseError(new RuntimeException(failures.prettyPrint()))
          case Right(config)  => IO.pure(config)
        })

      dataSource: HikariDataSource = DataSourceBuilder.buildDataSource(config.database)
      jdbcConnectionEC <- buildDatabaseConnectionEC(config.database.executionContext)
      transactor = HikariTransactor[IO](dataSource, jdbcConnectionEC)

      httpClient <- BlazeClientBuilder[IO].withConnectTimeout(1.minute).withIdleTimeout(5.minutes).resource

    } yield (config, transactor, httpClient)

    resources.use { case (config, transactor, httpClient) =>
      for {
        _ <- logger.info(s"Starting api-key-steward service with the following configuration: ${config.show}")

        migrationResult <- DatabaseMigrator.migrateDatabase(transactor.kernel, config.database)
        _ <- logger.info(s"Finished [${migrationResult.migrationsExecuted}] database migrations.")

        apiKeyPrefixProvider: ApiKeyPrefixProvider = new ApiKeyPrefixProvider(config.apiKey)
        randomStringGenerator: RandomStringGenerator = new RandomStringGenerator(config.apiKey)
        checksumCalculator: CRC32ChecksumCalculator = new CRC32ChecksumCalculator
        checksumCodec: ChecksumCodec = new ChecksumCodec
        apiKeyGenerator: ApiKeyGenerator = new ApiKeyGenerator(
          apiKeyPrefixProvider,
          randomStringGenerator,
          checksumCalculator,
          checksumCodec
        )

        apiKeyRepository: ApiKeyRepository = buildApiKeyRepository(config, transactor)
        apiKeyTemplateRepository: ApiKeyTemplateRepository = buildApiKeyTemplateRepository(transactor)
        apiKeyTemplatesPermissionsRepository: ApiKeyTemplatesPermissionsRepository =
          buildApiKeyTemplatesPermissionsRepository(transactor)
        permissionRepository: PermissionRepository = buildPermissionRepository(transactor)
        applicationRepository: ApplicationRepository = buildApplicationRepository(permissionRepository)(transactor)
        userRepository: UserRepository = buildUserRepository(transactor)
        tenantRepository: TenantRepository = buildTenantRepository(
          applicationRepository,
          apiKeyTemplateRepository,
          userRepository
        )(transactor)

        apiKeyValidationService = new ApiKeyValidationService(checksumCalculator, checksumCodec, apiKeyRepository)
        uuidGenerator = new UuidGenerator
        createApiKeyRequestValidator = new CreateApiKeyRequestValidator(config.apiKey)
        apiKeyManagementService = new ApiKeyManagementService(
          createApiKeyRequestValidator,
          apiKeyGenerator,
          uuidGenerator,
          apiKeyRepository
        )

        apiKeyTemplateService = new ApiKeyTemplateService(
          uuidGenerator,
          apiKeyTemplateRepository,
          apiKeyTemplatesPermissionsRepository
        )

        tenantService = new TenantService(uuidGenerator, tenantRepository)
        applicationService = new ApplicationService(uuidGenerator, applicationRepository)
        permissionService = new PermissionService(uuidGenerator, permissionRepository, applicationRepository)
        userService = new UserService(userRepository, tenantRepository, apiKeyTemplateRepository)

        validateRoutes = new ApiKeyValidationRoutes(apiKeyValidationService).allRoutes

        jwtOps = new JwtOps
        jwtAuthorizer = buildJwtAuthorizer(config, httpClient)
        userApiKeyManagementRoutes = new ApiKeyManagementRoutes(
          jwtOps,
          jwtAuthorizer,
          apiKeyManagementService
        ).allRoutes
        apiKeyManagementRoutes = new AdminApiKeyManagementRoutes(jwtAuthorizer, apiKeyManagementService).allRoutes

        apiKeyTemplateRoutes = new AdminApiKeyTemplateRoutes(
          jwtAuthorizer,
          apiKeyTemplateService,
          permissionService,
          userService
        ).allRoutes

        tenantRoutes = new AdminTenantRoutes(jwtAuthorizer, tenantService).allRoutes
        applicationRoutes = new AdminApplicationRoutes(jwtAuthorizer, applicationService).allRoutes
        permissionRoutes = new AdminPermissionRoutes(jwtAuthorizer, permissionService).allRoutes
        userRoutes = new AdminUserRoutes(jwtAuthorizer, userService).allRoutes

        documentationRoutes = new DocumentationRoutes().allRoutes

        httpApp = CORS.policy
          .withAllowOriginAll(
            documentationRoutes <+>
              validateRoutes <+>
              userApiKeyManagementRoutes <+>
              apiKeyManagementRoutes <+>
              apiKeyTemplateRoutes <+>
              tenantRoutes <+>
              applicationRoutes <+>
              permissionRoutes <+>
              userRoutes
          )
          .orNotFound

        app = buildServerResource(httpApp, config)
        _ <- app.useForever
        _ <- IO.blocking(transactor.kernel.close())
      } yield ()
    }
  }

  private def buildDatabaseConnectionEC(
      configOpt: Option[DatabaseConnectionExecutionContextConfig]
  ): Resource[IO, ExecutionContext] = {
    val threadPoolSize = configOpt match {
      case Some(DatabaseConnectionExecutionContextConfig(poolSize)) => IO.pure(poolSize)
      case None                                                     => IO.blocking(Runtime.getRuntime.availableProcessors).map(_ + 2)
    }

    Resource.eval(threadPoolSize).flatMap(ExecutionContexts.fixedThreadPool[IO])
  }

  private def buildApiKeyRepository(config: AppConfig, transactor: HikariTransactor[IO]) = {
    val apiKeyDb = new ApiKeyDb
    val apiKeyDataDb = new ApiKeyDataDb

    val secureHashGenerator: SecureHashGenerator = new SecureHashGenerator(config.apiKey.storageHashingAlgorithm)

    new ApiKeyRepository(apiKeyDb, apiKeyDataDb, secureHashGenerator)(transactor)
  }

  private def buildApiKeyTemplateRepository(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val apiKeyTemplateDb = new ApiKeyTemplateDb
    val permissionDb = new PermissionDb
    val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb

    new ApiKeyTemplateRepository(tenantDb, apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)(transactor)
  }

  private def buildApiKeyTemplatesPermissionsRepository(transactor: HikariTransactor[IO]) = {
    val apiKeyTemplateDb = new ApiKeyTemplateDb
    val permissionDb = new PermissionDb
    val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb

    new ApiKeyTemplatesPermissionsRepository(apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)(transactor)
  }

  private def buildTenantRepository(
      applicationRepository: ApplicationRepository,
      apiKeyTemplateRepository: ApiKeyTemplateRepository,
      userRepository: UserRepository
  )(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb

    new TenantRepository(tenantDb, applicationRepository, apiKeyTemplateRepository, userRepository)(transactor)
  }

  private def buildApplicationRepository(
      permissionRepository: PermissionRepository
  )(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val applicationDb = new ApplicationDb
    val permissionDb = new PermissionDb

    new ApplicationRepository(tenantDb, applicationDb, permissionDb, permissionRepository)(transactor)
  }

  private def buildPermissionRepository(transactor: HikariTransactor[IO]) = {
    val applicationDb = new ApplicationDb
    val permissionDb = new PermissionDb
    val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb

    new PermissionRepository(applicationDb, permissionDb, apiKeyTemplatesPermissionsDb)(transactor)
  }

  private def buildUserRepository(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val userDb = new UserDb

    new UserRepository(tenantDb, userDb)(transactor)
  }

  private def buildJwtAuthorizer(config: AppConfig, httpClient: Client[IO]): JwtAuthorizer = {
    val jwtValidator: JwtValidator = new JwtValidator(config.auth.jwt)
    val jwkProvider: JwkProvider = new UrlJwkProvider(config.auth.jwks, httpClient)(runtime)
    val publicKeyGenerator = new PublicKeyGenerator
    val jwtCustom = new JwtCustom(clock, config.auth.jwt)
    val jwtDecoder = new JwtDecoder(jwtValidator, jwkProvider, publicKeyGenerator)(jwtCustom)

    new JwtAuthorizer(jwtDecoder)
  }

  private def buildServerResource(httpApp: HttpApp[IO], config: AppConfig): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(config.http.host)
      .withPort(config.http.port)
      .withHttpApp(httpApp)
      .build

}
