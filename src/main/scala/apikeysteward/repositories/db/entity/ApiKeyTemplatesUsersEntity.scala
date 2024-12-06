package apikeysteward.repositories.db.entity

import java.util.UUID

object ApiKeyTemplatesUsersEntity {

  case class Read(
      tenantId: UUID,
      apiKeyTemplateId: UUID,
      userId: UUID
  )

  case class Write(
      tenantId: UUID,
      apiKeyTemplateId: UUID,
      userId: UUID
  )

}
