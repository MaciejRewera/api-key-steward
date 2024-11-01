package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.model.ApiKeyTemplate
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApiKeyTemplateResponse(
    apiKeyTemplate: ApiKeyTemplate
)

object CreateApiKeyTemplateResponse {
  implicit val codec: Codec[CreateApiKeyTemplateResponse] = deriveCodec[CreateApiKeyTemplateResponse]
}
