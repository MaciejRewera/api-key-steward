package apikeysteward.routes.model.admin.user

import apikeysteward.model.User
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteUserResponse(
    user: User
)

object DeleteUserResponse {
  implicit val codec: Codec[DeleteUserResponse] = deriveCodec[DeleteUserResponse]
}
