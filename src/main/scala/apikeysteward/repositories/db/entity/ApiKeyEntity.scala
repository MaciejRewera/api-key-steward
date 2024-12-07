package apikeysteward.repositories.db.entity

import java.time.Instant
import java.util.UUID

object ApiKeyEntity {

  case class Read(
      id: UUID,
      tenantId: UUID,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      tenantId: UUID,
      apiKey: String
  )
}
