package apikeysteward.repositories.db.entity

object TenantEntity {

  case class Read(
      id: Long,
      publicId: String,
      name: String
  )

  case class Write(
      publicId: String,
      name: String
  )
}
