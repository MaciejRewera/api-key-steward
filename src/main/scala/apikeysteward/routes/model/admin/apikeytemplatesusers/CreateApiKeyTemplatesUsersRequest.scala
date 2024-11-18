package apikeysteward.routes.model.admin.apikeytemplatesusers

import apikeysteward.model.User.UserId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateApiKeyTemplatesUsersRequest(
    userIds: List[UserId]
)

object CreateApiKeyTemplatesUsersRequest {
  implicit val codec: Codec[CreateApiKeyTemplatesUsersRequest] = deriveCodec[CreateApiKeyTemplatesUsersRequest]

  implicit val createApiKeyTemplatesUsersRequestSchema: Schema[CreateApiKeyTemplatesUsersRequest] =
    TapirCustomSchemas.createApiKeyTemplatesUsersRequestSchema
}
