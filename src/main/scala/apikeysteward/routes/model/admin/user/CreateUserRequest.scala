package apikeysteward.routes.model.admin.user

import apikeysteward.model.User.UserId
import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateUserRequest(
    userId: UserId
)

object CreateUserRequest {
  implicit val codec: Codec[CreateUserRequest] = deriveCodec[CreateUserRequest]

  implicit val createUserRequestSchema: Schema[CreateUserRequest] = TapirCustomSchemas.createUserRequestSchema
}
