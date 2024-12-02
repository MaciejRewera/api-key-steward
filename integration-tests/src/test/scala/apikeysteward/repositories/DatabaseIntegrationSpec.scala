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
import org.flywaydb.core.api.output.MigrateResult
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

  override def beforeAll(): Unit =
    (
      for {
        _ <- IO(super.beforeAll())
        _ <- resetDatabase
      } yield ()
    ).unsafeRunSync

  protected val resetDataQuery: ConnectionIO[?]

  override def beforeEach(): Unit =
    resetDataQuery.transact(transactor).unsafeRunSync()

  protected val resetDatabaseQuery: ConnectionIO[?] = ().pure[ConnectionIO]

  private def resetDatabase: IO[Unit] =
    resetDatabaseQuery.transact(transactor).void >> cleanAndMigrateDatabase.void

  private def cleanAndMigrateDatabase: IO[MigrateResult] =
    for {
      flyway <- DatabaseMigrator
        .buildFlywayConfigBase(dataSource, databaseConfig)
        .map(
          _.cleanDisabled(false)
            .baselineOnMigrate(true)
            .load()
        )

      _ <- IO.blocking(flyway.clean())
      res <- IO.blocking(flyway.migrate())
    } yield res

  override def afterAll(): Unit =
    (
      for {
        _ <- IO(super.afterAll())
        _ <- IO.blocking(dataSource.close())
      } yield ()
    ).unsafeRunSync
}
