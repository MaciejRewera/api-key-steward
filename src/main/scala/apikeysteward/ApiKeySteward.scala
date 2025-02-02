package apikeysteward

import apikeysteward.config.{AppConfig, DatabaseConnectionExecutionContextConfig}
import apikeysteward.generators._
import apikeysteward.repositories.{ApiKeyRepository, _}
import apikeysteward.repositories.db.{ApiKeysPermissionsDb, _}
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

        uuidGenerator = new UuidGenerator

        apiKeyRepository: ApiKeyRepository = buildApiKeyRepository(uuidGenerator, config)(transactor)
        apiKeyTemplateRepository: ApiKeyTemplateRepository = buildApiKeyTemplateRepository(uuidGenerator)(transactor)
        apiKeyTemplatesPermissionsRepository: ApiKeyTemplatesPermissionsRepository =
          buildApiKeyTemplatesPermissionsRepository(transactor)
        apiKeyTemplatesUsersRepository: ApiKeyTemplatesUsersRepository = buildApiKeyTemplatesUsersRepository(transactor)
        permissionRepository: PermissionRepository = buildPermissionRepository(uuidGenerator)(transactor)
        resourceServerRepository: ResourceServerRepository = buildResourceServerRepository(
          uuidGenerator,
          permissionRepository
        )(transactor)
        userRepository: UserRepository = buildUserRepository(uuidGenerator, apiKeyRepository)(transactor)
        tenantRepository: TenantRepository = buildTenantRepository(
          uuidGenerator,
          resourceServerRepository,
          apiKeyTemplateRepository,
          userRepository
        )(transactor)

        apiKeyPrefixProvider: ApiKeyPrefixProvider = new ApiKeyPrefixProvider(apiKeyTemplateRepository)
        randomStringGenerator: RandomStringGenerator = new RandomStringGenerator(config.apiKey)
        checksumCalculator: CRC32ChecksumCalculator = new CRC32ChecksumCalculator
        checksumCodec: ChecksumCodec = new ChecksumCodec
        apiKeyGenerator: ApiKeyGenerator = new ApiKeyGenerator(
          apiKeyPrefixProvider,
          randomStringGenerator,
          checksumCalculator,
          checksumCodec
        )

        apiKeyValidationService = new ApiKeyValidationService(checksumCalculator, checksumCodec, apiKeyRepository)

        createApiKeyRequestValidator = new CreateApiKeyRequestValidator(
          userRepository,
          apiKeyTemplateRepository
        )
        apiKeyManagementService = new ApiKeyManagementService(
          createApiKeyRequestValidator,
          apiKeyGenerator,
          uuidGenerator,
          apiKeyRepository,
          userRepository,
          permissionRepository
        )

        apiKeyTemplateService = new ApiKeyTemplateService(
          uuidGenerator,
          apiKeyTemplateRepository,
          userRepository
        )
        apiKeyTemplateAssociationsService = new ApiKeyTemplateAssociationsService(
          apiKeyTemplatesPermissionsRepository,
          apiKeyTemplatesUsersRepository
        )

        tenantService = new TenantService(uuidGenerator, tenantRepository)
        resourceServerService = new ResourceServerService(uuidGenerator, resourceServerRepository)
        permissionService = new PermissionService(
          uuidGenerator,
          permissionRepository,
          resourceServerRepository,
          apiKeyTemplateRepository
        )
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
          userService,
          apiKeyTemplateAssociationsService
        ).allRoutes

        tenantRoutes = new AdminTenantRoutes(jwtAuthorizer, tenantService).allRoutes
        resourceServerRoutes = new AdminResourceServerRoutes(jwtAuthorizer, resourceServerService).allRoutes
        permissionRoutes = new AdminPermissionRoutes(jwtAuthorizer, permissionService).allRoutes
        userRoutes = new AdminUserRoutes(
          jwtAuthorizer,
          userService,
          apiKeyTemplateService,
          apiKeyTemplateAssociationsService,
          apiKeyManagementService
        ).allRoutes

        documentationRoutes = new DocumentationRoutes().allRoutes

        httpApp = CORS.policy
          .withAllowOriginAll(
            documentationRoutes <+>
              validateRoutes <+>
              userApiKeyManagementRoutes <+>
              apiKeyManagementRoutes <+>
              apiKeyTemplateRoutes <+>
              tenantRoutes <+>
              resourceServerRoutes <+>
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

  private def buildApiKeyRepository(
      uuidGenerator: UuidGenerator,
      config: AppConfig
  )(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val apiKeyDb = new ApiKeyDb
    val apiKeyDataDb = new ApiKeyDataDb
    val permissionDb = new PermissionDb
    val userDb = new UserDb
    val apiKeyTemplateDb = new ApiKeyTemplateDb
    val apiKeysPermissionsDb = new ApiKeysPermissionsDb

    val secureHashGenerator: SecureHashGenerator = new SecureHashGenerator(config.apiKey.storageHashingAlgorithm)

    new ApiKeyRepository(
      uuidGenerator,
      secureHashGenerator,
      tenantDb,
      apiKeyDb,
      apiKeyDataDb,
      permissionDb,
      userDb,
      apiKeyTemplateDb,
      apiKeysPermissionsDb
    )(transactor)
  }

  private def buildApiKeyTemplateRepository(uuidGenerator: UuidGenerator)(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val apiKeyTemplateDb = new ApiKeyTemplateDb
    val permissionDb = new PermissionDb
    val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
    val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb

    new ApiKeyTemplateRepository(
      uuidGenerator,
      tenantDb,
      apiKeyTemplateDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeyTemplatesUsersDb
    )(transactor)
  }

  private def buildApiKeyTemplatesPermissionsRepository(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val apiKeyTemplateDb = new ApiKeyTemplateDb
    val permissionDb = new PermissionDb
    val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb

    new ApiKeyTemplatesPermissionsRepository(tenantDb, apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)(
      transactor
    )
  }

  private def buildApiKeyTemplatesUsersRepository(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val apiKeyTemplateDb = new ApiKeyTemplateDb
    val userDb = new UserDb
    val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb

    new ApiKeyTemplatesUsersRepository(tenantDb, apiKeyTemplateDb, userDb, apiKeyTemplatesUsersDb)(transactor)
  }

  private def buildTenantRepository(
      uuidGenerator: UuidGenerator,
      resourceServerRepository: ResourceServerRepository,
      apiKeyTemplateRepository: ApiKeyTemplateRepository,
      userRepository: UserRepository
  )(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb

    new TenantRepository(uuidGenerator, tenantDb, resourceServerRepository, apiKeyTemplateRepository, userRepository)(
      transactor
    )
  }

  private def buildResourceServerRepository(
      uuidGenerator: UuidGenerator,
      permissionRepository: PermissionRepository
  )(transactor: HikariTransactor[IO]) = {
    val tenantDb = new TenantDb
    val resourceServerDb = new ResourceServerDb
    val permissionDb = new PermissionDb

    new ResourceServerRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)(
      transactor
    )
  }

  private def buildPermissionRepository(uuidGenerator: UuidGenerator)(transactor: HikariTransactor[IO]) = {
    val resourceServerDb = new ResourceServerDb
    val tenantDb = new TenantDb
    val permissionDb = new PermissionDb
    val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
    val apiKeysPermissionsDb = new ApiKeysPermissionsDb

    new PermissionRepository(
      uuidGenerator,
      tenantDb,
      resourceServerDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeysPermissionsDb
    )(transactor)
  }

  private def buildUserRepository(uuidGenerator: UuidGenerator, apiKeyRepository: ApiKeyRepository)(
      transactor: HikariTransactor[IO]
  ) = {
    val tenantDb = new TenantDb
    val userDb = new UserDb
    val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb

    new UserRepository(uuidGenerator, tenantDb, userDb, apiKeyTemplatesUsersDb, apiKeyRepository)(transactor)
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
