package apikeysteward.routes.model.apikey

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId

import scala.concurrent.duration.Duration

trait CreateApiKeyRequestBase {
  val name: String
  val description: Option[String]
  val templateId: ApiKeyTemplateId
  val ttl: Duration
  val permissionIds: List[PermissionId]
}
