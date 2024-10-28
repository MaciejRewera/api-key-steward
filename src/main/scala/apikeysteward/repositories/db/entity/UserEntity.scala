package apikeysteward.repositories.db.entity

import apikeysteward.model.User

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

  object Write {
    def from(tenantId: Long, user: User): UserEntity.Write =
      UserEntity.Write(
        tenantId = tenantId,
        publicUserId = user.userId
      )
  }
}
