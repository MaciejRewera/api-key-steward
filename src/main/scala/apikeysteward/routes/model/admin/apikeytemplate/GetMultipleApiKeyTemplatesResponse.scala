package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.model.ApiKeyTemplate
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleApiKeyTemplatesResponse(
    apiKeyTemplates: List[ApiKeyTemplate]
)

object GetMultipleApiKeyTemplatesResponse {
  implicit val codec: Codec[GetMultipleApiKeyTemplatesResponse] = deriveCodec[GetMultipleApiKeyTemplatesResponse]
}
