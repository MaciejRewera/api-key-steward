package apikeysteward.repositories

import apikeysteward.config.DatabaseConfig
import cats.effect.IO
import com.zaxxer.hikari.HikariDataSource
import fly4s.core.Fly4s
import fly4s.core.data.{Fly4sConfig, Locations, MigrateResult}

object DatabaseMigrator {

  def migrateDatabase(dataSource: HikariDataSource, databaseConfig: DatabaseConfig): IO[MigrateResult] =
    Fly4s
      .makeFor[IO](
        acquireDataSource = IO(dataSource),
        config = Fly4sConfig(
          table = databaseConfig.migrationsTable,
          locations = Locations(databaseConfig.migrationsLocations),
          cleanDisabled = true
        )
      )
      .use { fly4s =>
        for {
          res <- fly4s.migrate
        } yield res
      }
}
