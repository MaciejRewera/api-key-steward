package apikeysteward.repositories.db.entity

object ApiKeyTemplatesPermissionsEntity {

  case class Read(
      apiKeyTemplateId: Long,
      permissionId: Long
  )

  case class Write(
      apiKeyTemplateId: Long,
      permissionId: Long
  )

}
