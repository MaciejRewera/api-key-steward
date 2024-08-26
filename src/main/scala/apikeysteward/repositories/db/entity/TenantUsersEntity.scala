package apikeysteward.repositories.db.entity

object TenantUsersEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      userId: String
  )

  case class Write(
    tenantId: Long,
    userId: String
  )
}
