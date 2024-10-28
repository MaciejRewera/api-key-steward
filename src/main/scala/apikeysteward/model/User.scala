package apikeysteward.model

import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class User(userId: UserId)

object User {
  implicit val codec: Codec[User] = deriveCodec[User]

  type UserId = String

  def from(userEntity: UserEntity.Read): User = User(userId = userEntity.publicUserId)
}
