package apikeysteward.repositories.db.entity

import apikeysteward.model.User

import java.time.Instant
import java.util.UUID

object UserEntity {

  case class Read(
      id: UUID,
      tenantId: UUID,
      publicUserId: String,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      tenantId: UUID,
      publicUserId: String
  )

  object Write {
    def from(id: UUID, tenantId: UUID, user: User): UserEntity.Write =
      UserEntity.Write(
        id = id,
        tenantId = tenantId,
        publicUserId = user.userId
      )
  }
}
