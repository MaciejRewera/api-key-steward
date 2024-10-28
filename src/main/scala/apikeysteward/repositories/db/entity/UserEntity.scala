package apikeysteward.repositories.db.entity

import java.time.Instant

object UserEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      publicUserId: String,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      tenantId: Long,
      publicUserId: String
  )

  object Write {}
}
