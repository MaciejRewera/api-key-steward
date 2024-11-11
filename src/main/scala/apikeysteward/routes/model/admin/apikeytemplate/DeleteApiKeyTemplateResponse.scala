package apikeysteward.routes.model.admin.apikeytemplate

import apikeysteward.model.ApiKeyTemplate
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteApiKeyTemplateResponse(
    template: ApiKeyTemplate
)

object DeleteApiKeyTemplateResponse {
  implicit val codec: Codec[DeleteApiKeyTemplateResponse] = deriveCodec[DeleteApiKeyTemplateResponse]
}
