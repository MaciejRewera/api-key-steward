package apikeysteward.routes.model.admin.apikeytemplatesusers

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class DeleteApiKeyTemplatesFromUserRequest(
    templateIds: List[ApiKeyTemplateId]
)

object DeleteApiKeyTemplatesFromUserRequest {
  implicit val codec: Codec[DeleteApiKeyTemplatesFromUserRequest] = deriveCodec[DeleteApiKeyTemplatesFromUserRequest]

  implicit val deleteApiKeyTemplatesWithUserRequestSchema: Schema[DeleteApiKeyTemplatesFromUserRequest] =
    TapirCustomSchemas.deleteApiKeyTemplatesWithUserRequestSchema

}
