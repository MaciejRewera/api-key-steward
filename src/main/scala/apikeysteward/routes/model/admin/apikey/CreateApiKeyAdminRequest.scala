package apikeysteward.routes.model.admin.apikey

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.User.UserId
import apikeysteward.routes.model.apikey.{CreateApiKeyRequest, CreateApiKeyRequestBase}
import apikeysteward.routes.model.{CodecCommons, TapirCustomSchemas}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import scala.concurrent.duration.Duration

case class CreateApiKeyAdminRequest(
    userId: UserId,
    override val name: String,
    override val description: Option[String],
    override val ttl: Duration,
    override val templateId: ApiKeyTemplateId,
    override val permissionIds: List[PermissionId]
) extends CreateApiKeyRequestBase {

  def toUserRequest: (String, CreateApiKeyRequest) = userId -> CreateApiKeyRequest(
    name = this.name,
    description = this.description,
    ttl = this.ttl,
    templateId = this.templateId,
    permissionIds = this.permissionIds
  )

}

object CreateApiKeyAdminRequest extends CodecCommons {
  implicit val codec: Codec[CreateApiKeyAdminRequest] = deriveCodec[CreateApiKeyAdminRequest]

  implicit val createApiKeyAdminRequestSchema: Schema[CreateApiKeyAdminRequest] =
    TapirCustomSchemas.createApiKeyAdminRequestSchema

}
