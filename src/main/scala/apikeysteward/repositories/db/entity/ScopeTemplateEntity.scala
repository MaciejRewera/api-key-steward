package apikeysteward.repositories.db.entity

object ScopeTemplateEntity {

  case class Read(
      id: Long,
      apiKeyTemplateId: Long,
      value: String,
      name: String,
      description: Option[String] = None
  )

  case class Write(
      apiKeyTemplateId: Long,
      value: String,
      name: String,
      description: Option[String] = None
  )

}
