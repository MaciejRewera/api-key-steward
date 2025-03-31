package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantDbId_1
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

  val apiKeyTemplatesPermissionsEntityWrite_1_1: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_1, permissionId = permissionDbId_1)

  val apiKeyTemplatesPermissionsEntityWrite_1_2: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_1, permissionId = permissionDbId_2)

  val apiKeyTemplatesPermissionsEntityWrite_1_3: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_1, permissionId = permissionDbId_3)

  val apiKeyTemplatesPermissionsEntityWrite_2_1: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_2, permissionId = permissionDbId_1)

  val apiKeyTemplatesPermissionsEntityWrite_2_2: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_2, permissionId = permissionDbId_2)

  val apiKeyTemplatesPermissionsEntityWrite_2_3: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_2, permissionId = permissionDbId_3)

  val apiKeyTemplatesPermissionsEntityWrite_3_1: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_3, permissionId = permissionDbId_1)

  val apiKeyTemplatesPermissionsEntityWrite_3_2: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_3, permissionId = permissionDbId_2)

  val apiKeyTemplatesPermissionsEntityWrite_3_3: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateDbId_3, permissionId = permissionDbId_3)

  val apiKeyTemplatesPermissionsEntitiesWrite: List[ApiKeyTemplatesPermissionsEntity.Write] = List(
    apiKeyTemplatesPermissionsEntityWrite_1_1,
    apiKeyTemplatesPermissionsEntityWrite_1_2,
    apiKeyTemplatesPermissionsEntityWrite_1_3
  )

  val apiKeyTemplatesPermissionsEntitiesRead: List[ApiKeyTemplatesPermissionsEntity.Read] =
    apiKeyTemplatesPermissionsEntitiesWrite.map { entityWrite =>
      ApiKeyTemplatesPermissionsEntity.Read(
        tenantId = entityWrite.tenantId,
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        permissionId = entityWrite.permissionId
      )
    }

  implicit class apiKeyTemplatesPermissionsEntityWriteToRead(
      entityWrite: ApiKeyTemplatesPermissionsEntity.Write
  ) {

    def toRead: ApiKeyTemplatesPermissionsEntity.Read = ApiKeyTemplatesPermissionsEntity.Read(
      tenantId = entityWrite.tenantId,
      apiKeyTemplateId = entityWrite.apiKeyTemplateId,
      permissionId = entityWrite.permissionId
    )

  }

}
