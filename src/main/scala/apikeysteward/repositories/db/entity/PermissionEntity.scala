package apikeysteward.repositories.db.entity

import apikeysteward.model.Permission

import java.time.Instant
import java.util.UUID

object PermissionEntity {

  case class Read(
      id: UUID,
      tenantId: UUID,
      resourceServerId: UUID,
      publicPermissionId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      tenantId: UUID,
      resourceServerId: UUID,
      publicPermissionId: String,
      name: String,
      description: Option[String]
  )

  object Write {

    def from(tenantId: UUID, resourceServerId: UUID, id: UUID, permission: Permission): PermissionEntity.Write =
      PermissionEntity.Write(
        id = id,
        tenantId = tenantId,
        resourceServerId = resourceServerId,
        publicPermissionId = permission.publicPermissionId.toString,
        name = permission.name,
        description = permission.description
      )

  }

}
