package apikeysteward.repositories.db.entity

object ScopeEntity {

  case class Read(
      id: Long,
      scope: String
  )

  case class Write(
      scope: String
  )
}
