package apikeysteward.repositories.db.entity

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

  object Write {}

}
