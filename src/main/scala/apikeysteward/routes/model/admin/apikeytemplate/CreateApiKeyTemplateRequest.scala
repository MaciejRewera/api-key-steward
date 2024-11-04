package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.routes.model.{CodecCommons, TapirCustomSchemas}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import scala.concurrent.duration.Duration

case class CreateApiKeyTemplateRequest(
    isDefault: Boolean,
    name: String,
    description: Option[String],
    apiKeyMaxExpiryPeriod: Duration
)

object CreateApiKeyTemplateRequest extends CodecCommons {
  implicit val codec: Codec[CreateApiKeyTemplateRequest] = deriveCodec[CreateApiKeyTemplateRequest]

  implicit val createApiKeyTemplateRequestSchema: Schema[CreateApiKeyTemplateRequest] =
    TapirCustomSchemas.createApiKeyTemplateRequestSchema
}