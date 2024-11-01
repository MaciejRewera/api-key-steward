package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.routes.model.{CodecCommons, TapirCustomSchemas}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

import scala.concurrent.duration.Duration

case class UpdateApiKeyTemplateRequest(
    isDefault: Boolean,
    name: String,
    description: Option[String],
    apiKeyMaxExpiryPeriod: Duration
)

object UpdateApiKeyTemplateRequest extends CodecCommons {
  implicit val codec: Codec[UpdateApiKeyTemplateRequest] = deriveCodec[UpdateApiKeyTemplateRequest]

  implicit val updateApiKeyTemplateRequestSchema: Schema[UpdateApiKeyTemplateRequest] =
    TapirCustomSchemas.updateApiKeyTemplateRequestSchema

}
