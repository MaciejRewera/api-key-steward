package apikeysteward.repositories.db

import apikeysteward.repositories.db.entity.ApiKeyDataScopesEntity
import doobie.Update
import doobie.implicits.toSqlInterpolator
import fs2.Stream

import java.time.{Clock, Instant}

class ApiKeyDataScopesDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insertMany(entities: List[ApiKeyDataScopesEntity.Write]): doobie.ConnectionIO[Int] = {
    val now = Instant.now(clock)
    Queries.insertMany.updateMany(entities.map((_, now, now)))
  }

  def getByApiKeyDataId(apiKeyDataId: Long): Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read] =
    Queries.getByApiKeyDataId(apiKeyDataId).stream

  def delete(apiKeyDataId: Long, scopeId: Long): doobie.ConnectionIO[Option[ApiKeyDataScopesEntity.Read]] =
    for {
      result <- Queries.getBy(apiKeyDataId, scopeId).option
      n <- Queries.delete(apiKeyDataId, scopeId).run
    } yield if (n > 0) result else None

  def delete(apiKeyDataId: Long): Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read] = {
    val result: doobie.ConnectionIO[List[ApiKeyDataScopesEntity.Read]] = for {
      res <- getByApiKeyDataId(apiKeyDataId).compile.toList
      _ <- Queries.delete(apiKeyDataId).run
    } yield res

    Stream.evals(result)
  }

  private object Queries {

    val insertMany: doobie.Update[(ApiKeyDataScopesEntity.Write, Instant, Instant)] = {
      val sql =
        "INSERT INTO api_key_data_scopes (api_key_data_id, scope_id, created_at, updated_at) VALUES (?, ?, ?, ?)"
      Update[(ApiKeyDataScopesEntity.Write, Instant, Instant)](sql)
    }

    def getByApiKeyDataId(apiKeyDataId: Long): doobie.Query0[ApiKeyDataScopesEntity.Read] =
      sql"""SELECT
           |  api_key_data_id,
           |  scope_id,
           |  created_at,
           |  updated_at
           |FROM api_key_data_scopes
           |WHERE api_key_data_id = $apiKeyDataId
           |""".stripMargin.query[ApiKeyDataScopesEntity.Read]

    def getBy(apiKeyDataId: Long, scopeId: Long): doobie.Query0[ApiKeyDataScopesEntity.Read] =
      sql"""SELECT
           |  api_key_data_id,
           |  scope_id,
           |  created_at,
           |  updated_at
           |FROM api_key_data_scopes
           |WHERE api_key_data_id = $apiKeyDataId AND scope_id = $scopeId
           |""".stripMargin.query[ApiKeyDataScopesEntity.Read]

    def delete(apiKeyDataId: Long, scopeId: Long): doobie.Update0 =
      sql"""DELETE FROM api_key_data_scopes
           |WHERE api_key_data_scopes.api_key_data_id = $apiKeyDataId AND api_key_data_scopes.scope_id = $scopeId
           """.stripMargin.update

    def delete(apiKeyDataId: Long): doobie.Update0 =
      sql"""DELETE FROM api_key_data_scopes
           |WHERE api_key_data_scopes.api_key_data_id = $apiKeyDataId
           """.stripMargin.update
  }
}
