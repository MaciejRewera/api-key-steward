package apikeysteward.repositories

import apikeysteward.config.{AppConfig, DatabaseConfig}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.transactor.Transactor
import fly4s.core.Fly4s
import fly4s.core.data.{Fly4sConfig, Locations, MigrateResult}
import org.scalatest.{AsyncTestSuite, BeforeAndAfterAll, BeforeAndAfterEach}
import pureconfig.ConfigSource

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait DatabaseIntegrationSpec extends BeforeAndAfterEach with BeforeAndAfterAll { this: AsyncTestSuite =>

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
  implicit val runtime: IORuntime = cats.effect.unsafe.implicits.global

  private lazy val databaseConfig: DatabaseConfig = ConfigSource.default
    .load[AppConfig]
    .leftMap(f => new RuntimeException(f.prettyPrint()))
    .map(_.database)
    .toTry
    .get

  private val dataSource: HikariDataSource = DataSourceBuilder.buildDataSource(databaseConfig)

  protected val transactor: Transactor[IO] = HikariTransactor[IO](dataSource, ec)

  protected val resetDataQuery: ConnectionIO[?]
  protected val resetDatabaseQuery: ConnectionIO[?] = ().pure[ConnectionIO]

  override def beforeAll(): Unit =
    (
      for {
        _ <- IO(super.beforeAll())
        _ <- resetDatabase
      } yield ()
    ).unsafeRunSync

  override def beforeEach(): Unit =
    resetDataQuery.transact(transactor).unsafeRunSync()

  private def resetDatabase: IO[Unit] =
    resetDatabaseQuery.transact(transactor).void >> cleanAndMigrateDatabase.void

  private def cleanAndMigrateDatabase: IO[MigrateResult] =
    Fly4s
      .makeFor[IO](
        acquireDataSource = IO(dataSource),
        config = Fly4sConfig(
          table = databaseConfig.migrationsTable,
          locations = Locations(databaseConfig.migrationsLocations),
          cleanDisabled = false
        )
      )
      .use { fly4s =>
        for {
          _ <- fly4s.clean
          res <- fly4s.migrate
        } yield res
      }
}
