package apikeysteward.repositories

import apikeysteward.config.DatabaseConfig
import cats.effect.IO
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.MigrateResult

object DatabaseMigrator {

  def buildFlywayConfigBase(dataSource: HikariDataSource, databaseConfig: DatabaseConfig): IO[FluentConfiguration] =
    IO.blocking {
      Flyway
        .configure()
        .dataSource(dataSource)
        .table(databaseConfig.migrationsTable)
        .locations(databaseConfig.migrationsLocations: _*)
    }

  def migrateDatabase(dataSource: HikariDataSource, databaseConfig: DatabaseConfig): IO[MigrateResult] =
    for {
      flyway <- buildFlywayConfigBase(dataSource, databaseConfig)
        .map(
          _.cleanDisabled(true)
            .validateMigrationNaming(true)
            .load()
        )

      res <- IO.blocking(flyway.migrate())
    } yield res

}
