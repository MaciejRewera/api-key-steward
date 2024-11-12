package apikeysteward.repositories.db.entity

import apikeysteward.model.Permission

import java.time.Instant

object PermissionEntity {

  case class Read(
      id: Long,
      applicationId: Long,
      publicPermissionId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      applicationId: Long,
      publicPermissionId: String,
      name: String,
      description: Option[String]
  )

  object Write {
    def from(applicationId: Long, permission: Permission): PermissionEntity.Write =
      PermissionEntity.Write(
        applicationId = applicationId,
        publicPermissionId = permission.publicPermissionId.toString,
        name = permission.name,
        description = permission.description
      )
  }

}
