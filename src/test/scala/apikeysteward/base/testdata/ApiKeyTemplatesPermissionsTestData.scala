package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.apiKeyTemplateEntityRead_1
import apikeysteward.base.testdata.PermissionsTestData.{
  permissionEntityRead_1,
  permissionEntityRead_2,
  permissionEntityRead_3
}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeyTemplatesPermissionsEntity, PermissionEntity}
import cats.implicits.catsSyntaxApplicativeId

object ApiKeyTemplatesPermissionsTestData extends FixedClock {

  val apiKeyTemplateEntityWrapped: doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Option(apiKeyTemplateEntityRead_1).pure[doobie.ConnectionIO]

  val permissionEntityWrapped_1: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_1).pure[doobie.ConnectionIO]
  val permissionEntityWrapped_2: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_2).pure[doobie.ConnectionIO]
  val permissionEntityWrapped_3: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_3).pure[doobie.ConnectionIO]

  val apiKeyTemplatesPermissionsEntitiesWrite: List[ApiKeyTemplatesPermissionsEntity.Write] = List(
    ApiKeyTemplatesPermissionsEntity
      .Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, permissionId = permissionEntityRead_1.id),
    ApiKeyTemplatesPermissionsEntity
      .Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, permissionId = permissionEntityRead_2.id),
    ApiKeyTemplatesPermissionsEntity
      .Write(apiKeyTemplateId = apiKeyTemplateEntityRead_1.id, permissionId = permissionEntityRead_3.id)
  )
  val apiKeyTemplatesPermissionsEntitiesRead: List[ApiKeyTemplatesPermissionsEntity.Read] =
    apiKeyTemplatesPermissionsEntitiesWrite.map { entityWrite =>
      ApiKeyTemplatesPermissionsEntity.Read(
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        permissionId = entityWrite.permissionId
      )
    }

}
