package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.model.ApiKeyTemplate
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UpdateApiKeyTemplateResponse(
  apiKeyTemplate: ApiKeyTemplate
)

object UpdateApiKeyTemplateResponse {
  implicit val codec: Codec[UpdateApiKeyTemplateResponse] = deriveCodec[UpdateApiKeyTemplateResponse]
}

