package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.model.ApiKeyTemplate
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSingleApiKeyTemplateResponse(
    template: ApiKeyTemplate
)

object GetSingleApiKeyTemplateResponse {
  implicit val codec: Codec[GetSingleApiKeyTemplateResponse] = deriveCodec[GetSingleApiKeyTemplateResponse]
}
