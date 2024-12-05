package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantDbId_1
import apikeysteward.base.testdata.UsersTestData._
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

  val apiKeyTemplatesUsersEntityWrite_1_1: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_1, userId = userDbId_1)

  val apiKeyTemplatesUsersEntityWrite_1_2: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_1, userId = userDbId_2)

  val apiKeyTemplatesUsersEntityWrite_1_3: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_1, userId = userDbId_3)

  val apiKeyTemplatesUsersEntityWrite_2_1: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_2, userId = userDbId_1)

  val apiKeyTemplatesUsersEntityWrite_2_2: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_2, userId = userDbId_2)

  val apiKeyTemplatesUsersEntityWrite_2_3: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_2, userId = userDbId_3)

  val apiKeyTemplatesUsersEntityWrite_3_1: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_3, userId = userDbId_1)

  val apiKeyTemplatesUsersEntityWrite_3_2: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_3, userId = userDbId_2)

  val apiKeyTemplatesUsersEntityWrite_3_3: ApiKeyTemplatesUsersEntity.Write =
    ApiKeyTemplatesUsersEntity.Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_3, userId = userDbId_3)

  val apiKeyTemplatesUsersEntitiesWrite_sameTemplate: List[ApiKeyTemplatesUsersEntity.Write] = List(
    apiKeyTemplatesUsersEntityWrite_1_1,
    apiKeyTemplatesUsersEntityWrite_1_2,
    apiKeyTemplatesUsersEntityWrite_1_3
  )
  val apiKeyTemplatesUsersEntitiesRead_sameTemplate: List[ApiKeyTemplatesUsersEntity.Read] =
    apiKeyTemplatesUsersEntitiesWrite_sameTemplate.map { entityWrite =>
      ApiKeyTemplatesUsersEntity.Read(
        tenantId = entityWrite.tenantId,
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        userId = entityWrite.userId
      )
    }

  val apiKeyTemplatesUsersEntitiesWrite_sameUser: List[ApiKeyTemplatesUsersEntity.Write] = List(
    apiKeyTemplatesUsersEntityWrite_1_1,
    apiKeyTemplatesUsersEntityWrite_2_1,
    apiKeyTemplatesUsersEntityWrite_3_1
  )
  val apiKeyTemplatesUsersEntitiesRead_sameUser: List[ApiKeyTemplatesUsersEntity.Read] =
    apiKeyTemplatesUsersEntitiesWrite_sameUser.map { entityWrite =>
      ApiKeyTemplatesUsersEntity.Read(
        tenantId = entityWrite.tenantId,
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        userId = entityWrite.userId
      )
    }

}
