package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{
  apiKeyTemplateEntityRead_1,
  apiKeyTemplateEntityRead_2,
  apiKeyTemplateEntityRead_3
}
import apikeysteward.base.testdata.UsersTestData.{userEntityRead_1, userEntityRead_2, userEntityRead_3}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeyTemplatesUsersEntity, UserEntity}
import cats.implicits.catsSyntaxApplicativeId
import doobie.ConnectionIO

object ApiKeyTemplatesUsersTestData extends FixedClock {

  val apiKeyTemplateEntityWrapped_1: ConnectionIO[Option[ApiKeyTemplateEntity.Read]] = wrap(apiKeyTemplateEntityRead_1)
  val apiKeyTemplateEntityWrapped_2: ConnectionIO[Option[ApiKeyTemplateEntity.Read]] = wrap(apiKeyTemplateEntityRead_2)
  val apiKeyTemplateEntityWrapped_3: ConnectionIO[Option[ApiKeyTemplateEntity.Read]] = wrap(apiKeyTemplateEntityRead_3)

  val userEntityWrapped_1: ConnectionIO[Option[UserEntity.Read]] = wrap(userEntityRead_1)
  val userEntityWrapped_2: ConnectionIO[Option[UserEntity.Read]] = wrap(userEntityRead_2)
  val userEntityWrapped_3: ConnectionIO[Option[UserEntity.Read]] = wrap(userEntityRead_3)

  private def wrap[A](a: A): ConnectionIO[Option[A]] = Option(a).pure[ConnectionIO]

  val apiKeyTemplatesUsersEntitiesWrite_sameTemplate: List[ApiKeyTemplatesUsersEntity.Write] = List(
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_1.id),
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_2.id),
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_3.id)
  )
  val apiKeyTemplatesUsersEntitiesRead_sameTemplate: List[ApiKeyTemplatesUsersEntity.Read] =
    apiKeyTemplatesUsersEntitiesWrite_sameTemplate.map { entityWrite =>
      ApiKeyTemplatesUsersEntity.Read(
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        userId = entityWrite.userId
      )
    }

  val apiKeyTemplatesUsersEntitiesWrite_sameUser: List[ApiKeyTemplatesUsersEntity.Write] = List(
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_1.id),
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_2.id, userId = userEntityRead_1.id),
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_3.id, userId = userEntityRead_1.id)
  )
  val apiKeyTemplatesUsersEntitiesRead_sameUser: List[ApiKeyTemplatesUsersEntity.Read] =
    apiKeyTemplatesUsersEntitiesWrite_sameUser.map { entityWrite =>
      ApiKeyTemplatesUsersEntity.Read(
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        userId = entityWrite.userId
      )
    }

}
