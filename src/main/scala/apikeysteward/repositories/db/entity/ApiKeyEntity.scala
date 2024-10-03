package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyEntity {

  case class Read(
      id: Long,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      apiKey: String
  )
}
