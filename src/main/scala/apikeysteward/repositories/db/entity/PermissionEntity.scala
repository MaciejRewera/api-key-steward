package apikeysteward.repositories.db.entity

import apikeysteward.model.Permission

import java.time.Instant

object PermissionEntity {

  case class Read(
      id: Long,
      resourceServerId: Long,
      publicPermissionId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      resourceServerId: Long,
      publicPermissionId: String,
      name: String,
      description: Option[String]
  )

  object Write {
    def from(resourceServerId: Long, permission: Permission): PermissionEntity.Write =
      PermissionEntity.Write(
        resourceServerId = resourceServerId,
        publicPermissionId = permission.publicPermissionId.toString,
        name = permission.name,
        description = permission.description
      )
  }

}
