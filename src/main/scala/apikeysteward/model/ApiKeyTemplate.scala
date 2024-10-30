package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.routes.model.CodecCommons
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID
import scala.concurrent.duration.Duration

case class ApiKeyTemplate(
    publicTemplateId: ApiKeyTemplateId,
    isDefault: Boolean,
    name: String,
    description: Option[String],
    apiKeyMaxExpiryPeriod: Duration,
    permissions: List[Permission]
)

object ApiKeyTemplate extends CodecCommons {
  implicit val codec: Codec[ApiKeyTemplate] = deriveCodec[ApiKeyTemplate]

  type ApiKeyTemplateId = UUID
}
