package apikeysteward.repositories.db

import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.ApiKeyAlreadyExistsError
import apikeysteward.repositories.db.entity.ApiKeyEntity
import doobie.SqlState
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION

import java.time.{Clock, Instant}

class ApiKeyDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(apiKeyEntity: ApiKeyEntity.Write): doobie.ConnectionIO[Either[ApiKeyInsertionError, ApiKeyEntity.Read]] = {
    val now = Instant.now(clock)
    Queries
      .insert(apiKeyEntity, now)
      .withUniqueGeneratedKeys[ApiKeyEntity.Read]("id", "created_at", "updated_at")
      .attemptSomeSqlState[ApiKeyInsertionError] {
        case sqlState: SqlState if sqlState == UNIQUE_VIOLATION => ApiKeyAlreadyExistsError
      }
  }

  private object Queries {

//    def exists(apiKey: String): doobie.Query0[ApiKeyEntity.Read] =
//      sql"""SELECT id, created_at, updated_at FROM api_key WHERE api_key = $apiKey""".query[ApiKeyEntity.Read]

    def insert(apiKeyEntityWrite: ApiKeyEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO api_key(
           api_key,
           created_at,
           updated_at
         ) VALUES (
            ${apiKeyEntityWrite.apiKey},
            $now,
            $now
         )""".stripMargin.update

  }
}
