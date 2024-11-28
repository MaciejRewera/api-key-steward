package apikeysteward.repositories.db.entity

import apikeysteward.model.Permission

import java.time.Instant
import java.util.UUID

object PermissionEntity {

  case class Read(
      id: UUID,
      resourceServerId: UUID,
      publicPermissionId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      resourceServerId: UUID,
      publicPermissionId: String,
      name: String,
      description: Option[String]
  )

  object Write {
    def from(id: UUID, resourceServerId: UUID, permission: Permission): PermissionEntity.Write =
      PermissionEntity.Write(
        id = id,
        resourceServerId = resourceServerId,
        publicPermissionId = permission.publicPermissionId.toString,
        name = permission.name,
        description = permission.description
      )
  }

}
