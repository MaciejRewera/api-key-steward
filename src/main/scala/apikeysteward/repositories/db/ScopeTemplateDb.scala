package apikeysteward.repositories.db

import apikeysteward.repositories.db.DbCommons.ScopeTemplateInsertionError
import apikeysteward.repositories.db.DbCommons.ScopeTemplateInsertionError.ScopeTemplateAlreadyExistsError
import apikeysteward.repositories.db.entity.ScopeTemplateEntity
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION
import doobie.{SqlState, Update}
import fs2.Stream

class ScopeTemplateDb {

  def insertMany(
      scopeTemplateEntities: List[ScopeTemplateEntity.Write]
  ): doobie.ConnectionIO[Either[ScopeTemplateInsertionError, List[ScopeTemplateEntity.Read]]] =
    Queries.insertMany
      .updateManyWithGeneratedKeys[ScopeTemplateEntity.Read](
        "id",
        "api_key_template_id",
        "value",
        "name",
        "description"
      )(scopeTemplateEntities)
      .compile
      .toList
      .attemptSomeSqlState[ScopeTemplateInsertionError] {
        case sqlState: SqlState if sqlState == UNIQUE_VIOLATION => ScopeTemplateAlreadyExistsError
      }

  def getByApiKeyTemplateId(apiKeyTemplateId: Long): Stream[doobie.ConnectionIO, ScopeTemplateEntity.Read] =
    Queries.getByApiKeyTemplateId(apiKeyTemplateId).stream

  private object Queries {

    val insertMany: doobie.Update[ScopeTemplateEntity.Write] = {
      val sql =
        "INSERT INTO scope_template (api_key_template_id, value, name, description) VALUES (?, ?, ?, ?) RETURNING *"

      Update[ScopeTemplateEntity.Write](sql)
    }

    def getByApiKeyTemplateId(apiKeyTemplateId: Long): doobie.Query0[ScopeTemplateEntity.Read] =
      sql"""SELECT
           |  id,
           |  api_key_template_id,
           |  value,
           |  name,
           |  description
           |FROM scope_template
           |WHERE api_key_template_id = $apiKeyTemplateId
           |""".stripMargin.query[ScopeTemplateEntity.Read]

  }
}
