package apikeysteward

import apikeysteward.config.AppConfig
import apikeysteward.repositories.{DataSourceBuilder, DatabaseMigrator}
import apikeysteward.routes.ApiKeyRoutes
import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.ember.server.EmberServerBuilder
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

      httpClient <- BlazeClientBuilder[IO]
        .withRequestTimeout(1.minute)
        .withIdleTimeout(5.minutes)
        .resource

    } yield (config, transactor, httpClient)

    resources.use { case (config, transactor, httpClient) =>
      for {
        _ <- logger.info(s"Starting api-key-steward service with the following configuration: ${config.show}")

        migrationResult <- DatabaseMigrator.migrateDatabase(transactor.kernel, config.database)
        _ <- logger.info(s"Finished [${migrationResult.migrationsExecuted}] database migrations.")

        routes = new ApiKeyRoutes().allRoutes.orNotFound

        _ <- EmberServerBuilder
          .default[IO]
          .withHost(config.http.host)
          .withPort(config.http.port)
          .withHttpApp(routes)
          .build
          .useForever
      } yield ()
    }
  }

}
