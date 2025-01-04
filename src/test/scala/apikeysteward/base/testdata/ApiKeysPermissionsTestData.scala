package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantDbId_1
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeysPermissionsEntity, PermissionEntity}
import cats.implicits.catsSyntaxApplicativeId

object ApiKeysPermissionsTestData extends FixedClock {

  val permissionEntityWrapped_1: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_1).pure[doobie.ConnectionIO]
  val permissionEntityWrapped_2: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_2).pure[doobie.ConnectionIO]
  val permissionEntityWrapped_3: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_3).pure[doobie.ConnectionIO]

  val apiKeysPermissionsEntityWrite_1_1: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_1, permissionId = permissionDbId_1)

  val apiKeysPermissionsEntityWrite_1_2: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_1, permissionId = permissionDbId_2)

  val apiKeysPermissionsEntityWrite_1_3: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_1, permissionId = permissionDbId_3)

  val apiKeysPermissionsEntityWrite_2_1: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_2, permissionId = permissionDbId_1)

  val apiKeysPermissionsEntityWrite_2_2: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_2, permissionId = permissionDbId_2)

  val apiKeysPermissionsEntityWrite_2_3: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_2, permissionId = permissionDbId_3)

  val apiKeysPermissionsEntityWrite_3_1: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_3, permissionId = permissionDbId_1)

  val apiKeysPermissionsEntityWrite_3_2: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_3, permissionId = permissionDbId_2)

  val apiKeysPermissionsEntityWrite_3_3: ApiKeysPermissionsEntity.Write =
    ApiKeysPermissionsEntity
      .Write(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_3, permissionId = permissionDbId_3)

  val apiKeysPermissionsEntitiesWrite: List[ApiKeysPermissionsEntity.Write] = List(
    apiKeysPermissionsEntityWrite_1_1,
    apiKeysPermissionsEntityWrite_1_2,
    apiKeysPermissionsEntityWrite_1_3
  )

  val apiKeysPermissionsEntitiesRead: List[ApiKeysPermissionsEntity.Read] =
    apiKeysPermissionsEntitiesWrite.map { entityWrite =>
      ApiKeysPermissionsEntity.Read(
        tenantId = entityWrite.tenantId,
        apiKeyDataId = entityWrite.apiKeyDataId,
        permissionId = entityWrite.permissionId
      )
    }

}
