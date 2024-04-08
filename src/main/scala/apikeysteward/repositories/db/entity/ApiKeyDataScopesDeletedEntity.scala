package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyDataScopesDeletedEntity {

  case class Read(
      id: Long,
      deletedAt: Instant,
      apiKeyDataId: Long,
      scopeId: Long,
      createdAt: Instant,
      updatedAt: Instant
  )
}
