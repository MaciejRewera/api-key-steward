package apikeysteward.model

import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity
import apikeysteward.routes.model.admin.user.CreateUserRequest
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class User(userId: UserId)

object User {
  implicit val encoder: Encoder[User] = deriveEncoder[User].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[User] = deriveDecoder[User]

  type UserId = String

  def from(userEntity: UserEntity.Read): User = User(userId = userEntity.publicUserId)

  def from(createUserRequest: CreateUserRequest): User = User(userId = createUserRequest.userId)
}
