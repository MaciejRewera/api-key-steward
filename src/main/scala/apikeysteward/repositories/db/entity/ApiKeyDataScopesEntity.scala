package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyDataScopesEntity {

  case class Read(
      apiKeyDataId: Long,
      scopeId: Long,
      createdAt: Instant,
      updatedAt: Instant
  )

  case class Write(
      apiKeyDataId: Long,
      scopeId: Long
  )
}