package apikeysteward

import apikeysteward.config.{AppConfig, DatabaseConnectionExecutionContextConfig}
import apikeysteward.generators._
import apikeysteward.repositories._
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDataScopesDb, ApiKeyDb, ScopeDb}
import apikeysteward.routes.auth._
import apikeysteward.routes.auth.model.JwtCustom
import apikeysteward.routes.{AdminRoutes, ApiKeyValidationRoutes, DocumentationRoutes, ManagementRoutes}
import apikeysteward.services.{ApiKeyValidationService, CreateUpdateApiKeyRequestValidator, ManagementService}
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

object Application extends IOApp.Simple with Logging {

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

        apiKeyService = new ApiKeyValidationService(checksumCalculator, checksumCodec, apiKeyRepository)
        createApiKeyRequestValidator = new CreateUpdateApiKeyRequestValidator(config.apiKey)
        managementService = new ManagementService(createApiKeyRequestValidator, apiKeyGenerator, apiKeyRepository)

        validateRoutes = new ApiKeyValidationRoutes(apiKeyService).allRoutes

        jwtOps = new JwtOps
        jwtAuthorizer = buildJwtAuthorizer(config, httpClient)
        managementRoutes = new ManagementRoutes(jwtOps, jwtAuthorizer, managementService).allRoutes
        adminRoutes = new AdminRoutes(jwtAuthorizer, managementService).allRoutes

        documentationRoutes = new DocumentationRoutes().allRoutes

        httpApp = CORS.policy
          .withAllowOriginAll(
            validateRoutes <+> managementRoutes <+> adminRoutes <+> documentationRoutes
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
    val scopeDb = new ScopeDb
    val apiKeyDataScopesDb = new ApiKeyDataScopesDb

    val secureHashGenerator: SecureHashGenerator = new SecureHashGenerator(config.apiKey.storageHashingAlgorithm)

    new ApiKeyRepository(
      apiKeyDb,
      apiKeyDataDb,
      scopeDb,
      apiKeyDataScopesDb,
      secureHashGenerator
    )(transactor)
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
