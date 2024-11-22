package apikeysteward.routes.model.admin.apikeytemplatesusers

import apikeysteward.model.User.UserId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class AssociateUsersWithApiKeyTemplateRequest(
    userIds: List[UserId]
)

object AssociateUsersWithApiKeyTemplateRequest {
  implicit val codec: Codec[AssociateUsersWithApiKeyTemplateRequest] =
    deriveCodec[AssociateUsersWithApiKeyTemplateRequest]

  implicit val associateUsersWithApiKeyTemplateRequestSchema: Schema[AssociateUsersWithApiKeyTemplateRequest] =
    TapirCustomSchemas.associateUsersWithApiKeyTemplateRequestSchema
}
