package apikeysteward.routes.model.admin.user

import apikeysteward.model.User
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSingleUserResponse(
    user: User
)

object GetSingleUserResponse {
  implicit val codec: Codec[GetSingleUserResponse] = deriveCodec[GetSingleUserResponse]
}
