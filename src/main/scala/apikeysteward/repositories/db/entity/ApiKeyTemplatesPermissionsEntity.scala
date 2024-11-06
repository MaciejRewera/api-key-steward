package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyTemplatesPermissionsEntity {

  case class Read(
      apiKeyTemplateId: Long,
      permissionId: Long,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      apiKeyTemplateId: Long,
      permissionId: Long
  )

}
