package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.TenantsTestData.{tenantDbId_1, tenantDbId_2, tenantDbId_3}
import apikeysteward.model.User
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity

import java.util.UUID
import scala.util.Random

object UsersTestData extends FixedClock {

  val userDbId_1: UUID = UUID.randomUUID()
  val userDbId_2: UUID = UUID.randomUUID()
  val userDbId_3: UUID = UUID.randomUUID()

  val publicUserId_1: UserId = Random.alphanumeric.take(42).mkString
  val publicUserId_2: UserId = Random.alphanumeric.take(42).mkString
  val publicUserId_3: UserId = Random.alphanumeric.take(42).mkString
  val publicUserId_4: UserId = Random.alphanumeric.take(42).mkString
  val publicUserIdStr_1: String = publicUserId_1
  val publicUserIdStr_2: String = publicUserId_2
  val publicUserIdStr_3: String = publicUserId_3
  val publicUserIdStr_4: String = publicUserId_4

  val user_1: User = User(userId = publicUserId_1)
  val user_2: User = User(userId = publicUserId_2)
  val user_3: User = User(userId = publicUserId_3)

  val userEntityWrite_1: UserEntity.Write = UserEntity.Write(
    id = userDbId_1,
    tenantId = tenantDbId_1,
    publicUserId = publicUserIdStr_1
  )
  val userEntityRead_1: UserEntity.Read = UserEntity.Read(
    id = userDbId_1,
    tenantId = tenantDbId_1,
    publicUserId = publicUserIdStr_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val userEntityWrite_2: UserEntity.Write = UserEntity.Write(
    id = userDbId_2,
    tenantId = tenantDbId_2,
    publicUserId = publicUserIdStr_2
  )
  val userEntityRead_2: UserEntity.Read = UserEntity.Read(
    id = userDbId_2,
    tenantId = tenantDbId_2,
    publicUserId = publicUserIdStr_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val userEntityWrite_3: UserEntity.Write = UserEntity.Write(
    id = userDbId_3,
    tenantId = tenantDbId_3,
    publicUserId = publicUserIdStr_3
  )
  val userEntityRead_3: UserEntity.Read = UserEntity.Read(
    id = userDbId_3,
    tenantId = tenantDbId_3,
    publicUserId = publicUserIdStr_3,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

}
