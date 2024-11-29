package apikeysteward.repositories.db.entity

import java.time.Instant
import java.util.UUID

object ApiKeyEntity {

  case class Read(
      id: UUID,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      apiKey: String
  )
}
