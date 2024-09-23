package apikeysteward.routes.model

trait CreateUpdateApiKeyRequestBase {
  val name: String
  val description: Option[String]
  val ttl: Int
  val scopes: List[String]
}
