package apikeysteward.repositories.db.entity

import java.util.UUID

object ApiKeyTemplatesUsersEntity {

  case class Read(
      apiKeyTemplateId: UUID,
      userId: UUID
  )

  case class Write(
      apiKeyTemplateId: UUID,
      userId: UUID
  )

}
