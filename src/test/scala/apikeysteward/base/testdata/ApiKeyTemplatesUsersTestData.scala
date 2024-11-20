package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.apiKeyTemplateEntityRead_1
import apikeysteward.base.testdata.UsersTestData.{userEntityRead_1, userEntityRead_2, userEntityRead_3}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeyTemplatesUsersEntity, UserEntity}
import cats.implicits.catsSyntaxApplicativeId

object ApiKeyTemplatesUsersTestData extends FixedClock {

  val apiKeyTemplateEntityWrapped: doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Option(apiKeyTemplateEntityRead_1).pure[doobie.ConnectionIO]

  val userEntityWrapped_1: doobie.ConnectionIO[Option[UserEntity.Read]] =
    Option(userEntityRead_1).pure[doobie.ConnectionIO]
  val userEntityWrapped_2: doobie.ConnectionIO[Option[UserEntity.Read]] =
    Option(userEntityRead_2).pure[doobie.ConnectionIO]
  val userEntityWrapped_3: doobie.ConnectionIO[Option[UserEntity.Read]] =
    Option(userEntityRead_3).pure[doobie.ConnectionIO]

  val apiKeyTemplatesUsersEntitiesWrite: List[ApiKeyTemplatesUsersEntity.Write] = List(
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_1.id),
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_2.id),
    ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, userId = userEntityRead_3.id)
  )

}
