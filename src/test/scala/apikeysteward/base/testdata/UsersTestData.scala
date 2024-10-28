package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.model.User
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity

import scala.util.Random

object UsersTestData extends FixedClock {

  val publicUserId_1: UserId = Random.nextString(42)
  val publicUserId_2: UserId = Random.nextString(42)
  val publicUserId_3: UserId = Random.nextString(42)
  val publicUserId_4: UserId = Random.nextString(42)
  val publicUserIdStr_1: String = publicUserId_1
  val publicUserIdStr_2: String = publicUserId_2
  val publicUserIdStr_3: String = publicUserId_3
  val publicUserIdStr_4: String = publicUserId_4

  val user_1: User = User(userId = publicUserId_1)
  val user_2: User = User(userId = publicUserId_2)
  val user_3: User = User(userId = publicUserId_3)

  val userEntityWrite_1: UserEntity.Write = UserEntity.Write(
    tenantId = 1L,
    publicUserId = publicUserIdStr_1
  )
  val userEntityRead_1: UserEntity.Read = UserEntity.Read(
    id = 1L,
    tenantId = 1L,
    publicUserId = publicUserIdStr_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val userEntityWrite_2: UserEntity.Write = UserEntity.Write(
    tenantId = 2L,
    publicUserId = publicUserIdStr_2
  )
  val userEntityRead_2: UserEntity.Read = UserEntity.Read(
    id = 2L,
    tenantId = 2L,
    publicUserId = publicUserIdStr_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val userEntityWrite_3: UserEntity.Write = UserEntity.Write(
    tenantId = 3L,
    publicUserId = publicUserIdStr_3
  )
  val userEntityRead_3: UserEntity.Read = UserEntity.Read(
    id = 3L,
    tenantId = 3L,
    publicUserId = publicUserIdStr_3,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

}
