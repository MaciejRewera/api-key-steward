package apikeysteward

import apikeysteward.config.AppConfig
import apikeysteward.generators.{ApiKeyGenerator, StringApiKeyGenerator}
import apikeysteward.license.AlwaysValidLicenseValidator
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDataScopesDb, ApiKeyDb, ScopeDb}
import apikeysteward.repositories.{ApiKeyRepository, DataSourceBuilder, DatabaseMigrator, DbApiKeyRepository}
import apikeysteward.routes.{AdminRoutes, DocumentationRoutes, ValidateApiKeyRoutes}
import apikeysteward.services.{AdminService, ApiKeyService, LicenseService}
import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.CORS
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource

import java.time.Clock
import scala.concurrent.duration.DurationInt

object Application extends IOApp.Simple {

  private implicit val clock: Clock = Clock.systemUTC()
  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

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

    } yield (config, transactor)

    resources.use { case (config, transactor) =>
      for {
        _ <- logger.info(s"Starting api-key-steward service with the following configuration: ${config.show}")

        migrationResult <- DatabaseMigrator.migrateDatabase(transactor.kernel, config.database)
        _ <- logger.info(s"Finished [${migrationResult.migrationsExecuted}] database migrations.")

        apiKeyGenerator: ApiKeyGenerator[String] = new StringApiKeyGenerator()

        apiKeyDb = new ApiKeyDb()
        apiKeyDataDb = new ApiKeyDataDb()
        scopeDb = new ScopeDb()
        apiKeyDataScopesDb = new ApiKeyDataScopesDb()

        apiKeyRepository: ApiKeyRepository[String] = new DbApiKeyRepository(
          apiKeyDb,
          apiKeyDataDb,
          scopeDb,
          apiKeyDataScopesDb
        )(transactor)

        apiKeyService = new ApiKeyService(apiKeyRepository)
        adminService = new AdminService[String](apiKeyGenerator, apiKeyRepository)

        validateRoutes = new ValidateApiKeyRoutes(apiKeyService).allRoutes
        adminRoutes = new AdminRoutes(adminService).allRoutes

        documentationRoutes = new DocumentationRoutes().allRoutes

        httpApp = CORS.policy
          .withAllowOriginAll(
            validateRoutes <+> adminRoutes <+> documentationRoutes
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
