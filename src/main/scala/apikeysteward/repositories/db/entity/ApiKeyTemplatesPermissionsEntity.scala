package apikeysteward.repositories.db.entity

import java.util.UUID

object ApiKeyTemplatesPermissionsEntity {

  case class Read(
      tenantId: UUID,
      apiKeyTemplateId: UUID,
      permissionId: UUID
  )

  case class Write(
      tenantId: UUID,
      apiKeyTemplateId: UUID,
      permissionId: UUID
  )

}
