package apikeysteward.routes.model.admin.user

import apikeysteward.model.User
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateUserResponse(
    user: User
)

object CreateUserResponse {
  implicit val codec: Codec[CreateUserResponse] = deriveCodec[CreateUserResponse]
}
