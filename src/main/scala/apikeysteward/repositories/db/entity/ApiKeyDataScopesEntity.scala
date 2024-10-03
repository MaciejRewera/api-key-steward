package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyDataScopesEntity {

  case class Read(
      apiKeyDataId: Long,
      scopeId: Long,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      apiKeyDataId: Long,
      scopeId: Long
  )
}
