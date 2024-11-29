package apikeysteward.repositories.db.entity

import java.util.UUID

object ApiKeyTemplatesPermissionsEntity {

  case class Read(
      apiKeyTemplateId: UUID,
      permissionId: UUID
  )

  case class Write(
      apiKeyTemplateId: UUID,
      permissionId: UUID
  )

}
