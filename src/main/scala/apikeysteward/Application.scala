package apikeysteward

import apikeysteward.config.AppConfig
import apikeysteward.generators._
import apikeysteward.license.AlwaysValidLicenseValidator
import apikeysteward.repositories._
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDataScopesDb, ApiKeyDb, ScopeDb}
import apikeysteward.routes.auth._
import apikeysteward.routes.{AdminRoutes, ApiKeyValidationRoutes, DocumentationRoutes, ManagementRoutes}
import apikeysteward.services.{ApiKeyValidationService, LicenseService, ManagementService}
import apikeysteward.utils.Logging
import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import org.http4s.HttpApp
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.CORS
import pureconfig.ConfigSource

import java.time.Clock
import scala.concurrent.duration.DurationInt

object Application extends IOApp.Simple with Logging {

  private implicit val clock: Clock = Clock.systemUTC()

  override def run: IO[Unit] = {

    val resources = for {
      config <- Resource
        .eval(ConfigSource.default.load[AppConfig] match {
          case Left(failures) => IO.raiseError(new RuntimeException(failures.prettyPrint()))
          case Right(config)  => IO.pure(config)
        })

      jdbcConnectionEC <- ExecutionContexts.fixedThreadPool[IO](16)
      dataSource: HikariDataSource = DataSourceBuilder.buildDataSource(config.database)
      transactor = HikariTransactor[IO](dataSource, jdbcConnectionEC)

      httpClient <- BlazeClientBuilder[IO].withConnectTimeout(1.minute).withIdleTimeout(5.minutes).resource

    } yield (config, transactor, httpClient)

    resources.use { case (config, transactor, httpClient) =>
      for {
        _ <- logger.info(s"Starting api-key-steward service with the following configuration: ${config.show}")

        migrationResult <- DatabaseMigrator.migrateDatabase(transactor.kernel, config.database)
        _ <- logger.info(s"Finished [${migrationResult.migrationsExecuted}] database migrations.")

        jwkProvider: JwkProvider = new UrlJwkProvider(config.auth.jwks, httpClient)(runtime)
        publicKeyGenerator = new PublicKeyGenerator(config.auth)
        jwtDecoder = new JwtDecoder(jwkProvider, publicKeyGenerator, config.auth)
        jwtValidator = new JwtValidator(jwtDecoder)

        apiKeyPrefixProvider: ApiKeyPrefixProvider = new ApiKeyPrefixProvider(config.apiKey)
        randomStringGenerator: RandomStringGenerator = new RandomStringGenerator(config.apiKey)
        checksumCalculator: CRC32ChecksumCalculator = new CRC32ChecksumCalculator()
        checksumCodec: ChecksumCodec = new ChecksumCodec()
        apiKeyGenerator: ApiKeyGenerator = new ApiKeyGenerator(
          apiKeyPrefixProvider,
          randomStringGenerator,
          checksumCalculator,
          checksumCodec
        )
        secureHashGenerator: SecureHashGenerator = new SecureHashGenerator(config.apiKey.storageHashingAlgorithm)

        apiKeyDb = new ApiKeyDb()
        apiKeyDataDb = new ApiKeyDataDb()
        scopeDb = new ScopeDb()
        apiKeyDataScopesDb = new ApiKeyDataScopesDb()

        apiKeyRepository: ApiKeyRepository = new DbApiKeyRepository(
          apiKeyDb,
          apiKeyDataDb,
          scopeDb,
          apiKeyDataScopesDb,
          secureHashGenerator
        )(transactor)

        apiKeyService = new ApiKeyValidationService(checksumCalculator, checksumCodec, apiKeyRepository)
        managementService = new ManagementService(apiKeyGenerator, apiKeyRepository)

        validateRoutes = new ApiKeyValidationRoutes(apiKeyService).allRoutes
        jwtOps = new JwtOps()
        managementRoutes = new ManagementRoutes(jwtOps, jwtValidator, managementService).allRoutes
        adminRoutes = new AdminRoutes(jwtValidator, managementService).allRoutes

        documentationRoutes = new DocumentationRoutes().allRoutes

        httpApp = CORS.policy
          .withAllowOriginAll(
            validateRoutes <+> managementRoutes <+> adminRoutes <+> documentationRoutes
          )
          .orNotFound

        licenseServiceConfig = LicenseService.Configuration(
          initialDelay = 15.minutes,
          validationPeriod = 24.hours,
          licenseConfig = config.license
        )
        licenseValidator = new AlwaysValidLicenseValidator
        licenseService = new LicenseService(licenseServiceConfig, licenseValidator)

        app = buildServerResource(httpApp, config)

        _ <- IO.race(licenseService.periodicallyValidateLicense(), app.useForever)
      } yield ()
    }
  }

  private def buildServerResource(httpApp: HttpApp[IO], config: AppConfig): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(config.http.host)
      .withPort(config.http.port)
      .withHttpApp(httpApp)
      .build

}
