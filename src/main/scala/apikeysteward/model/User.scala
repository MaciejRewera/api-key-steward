package apikeysteward.model

import apikeysteward.model.User.UserId
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class User(userId: UserId)

object User {
  implicit val codec: Codec[User] = deriveCodec[User]

  type UserId = String
}
