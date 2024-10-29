package apikeysteward.routes.model.admin.user

import apikeysteward.model.User
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleUsersResponse(
    users: List[User]
)

object GetMultipleUsersResponse {
  implicit val codec: Codec[GetMultipleUsersResponse] = deriveCodec[GetMultipleUsersResponse]
}
