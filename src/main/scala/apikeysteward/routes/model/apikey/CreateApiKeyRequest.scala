package apikeysteward.routes.model.apikey

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.routes.model.{CodecCommons, TapirCustomSchemas}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._

import scala.concurrent.duration.Duration

case class CreateApiKeyRequest(
    override val name: String,
    override val description: Option[String],
    override val ttl: Duration,
    override val templateId: ApiKeyTemplateId,
    override val permissionIds: List[PermissionId]
) extends CreateApiKeyRequestBase

object CreateApiKeyRequest extends CodecCommons {
  implicit val codec: Codec[CreateApiKeyRequest] = deriveCodec[CreateApiKeyRequest]

  implicit val createApiKeyRequestSchema: Schema[CreateApiKeyRequest] = TapirCustomSchemas.createApiKeyRequestSchema
}
