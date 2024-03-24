package apikeysteward.repositories.db

import apikeysteward.repositories.db.entity.ApiKeyDataScopesEntity
import doobie.Update
import doobie.implicits.toSqlInterpolator
import fs2.Stream

class ApiKeyDataScopesDb {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insertMany(apiKeyDataScopesEntities: List[ApiKeyDataScopesEntity.Write]): doobie.ConnectionIO[Int] =
    Queries.insertMany.updateMany(apiKeyDataScopesEntities)

  def getByApiKeyDataId(apiKeyDataId: Long): Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read] =
    Queries.getByApiKeyDataId(apiKeyDataId).stream

  private object Queries {

    val insertMany: doobie.Update[ApiKeyDataScopesEntity.Write] = {
      val sql = "INSERT INTO api_key_data_scopes (api_key_data_id, scope_id) VALUES (?, ?)"
      Update[ApiKeyDataScopesEntity.Write](sql)
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
  }
}
