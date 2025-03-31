package apikeysteward.routes.model.admin.apikeytemplatesusers

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class AssociateApiKeyTemplatesWithUserRequest(
    templateIds: List[ApiKeyTemplateId]
)

object AssociateApiKeyTemplatesWithUserRequest {

  implicit val codec: Codec[AssociateApiKeyTemplatesWithUserRequest] =
    deriveCodec[AssociateApiKeyTemplatesWithUserRequest]

  implicit val associateApiKeyTemplatesWithUserRequestSchema: Schema[AssociateApiKeyTemplatesWithUserRequest] =
    TapirCustomSchemas.associateApiKeyTemplatesWithUserRequestSchema

}
