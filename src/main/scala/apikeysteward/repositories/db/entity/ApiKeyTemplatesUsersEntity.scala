package apikeysteward.repositories.db.entity

object ApiKeyTemplatesUsersEntity {

  case class Read(
      apiKeyTemplateId: Long,
      userId: Long
  )

  case class Write(
      apiKeyTemplateId: Long,
      userId: Long
  )

}
