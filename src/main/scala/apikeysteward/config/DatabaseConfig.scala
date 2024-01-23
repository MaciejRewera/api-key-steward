package apikeysteward.config

import org.http4s.Uri

case class DatabaseConfig(
    uri: Uri,
    driver: String,
    username: Option[String],
    password: Option[String],
    migrationsTable: String,
    migrationsLocations: List[String]
)
