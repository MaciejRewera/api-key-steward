package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{
  apiKeyTemplateEntityWrite_1,
  apiKeyTemplateEntityWrite_2,
  apiKeyTemplateEntityWrite_3
}
import apikeysteward.base.testdata.ApplicationsTestData.applicationEntityWrite_1
import apikeysteward.base.testdata.PermissionsTestData.{
  permissionEntityWrite_1,
  permissionEntityWrite_2,
  permissionEntityWrite_3
}
import apikeysteward.base.testdata.TenantsTestData.tenantEntityWrite_1
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApplicationDb, PermissionDb, TenantDb}
import org.scalatest.EitherValues

private[repositories] object TestDataInsertions extends EitherValues {

  type TenantDbId = Long
  type ApplicationDbId = Long
  type PermissionDbId = Long
  type TemplateDbId = Long

  def insertPrerequisiteData(
      tenantDb: TenantDb,
      applicationDb: ApplicationDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb
  ): doobie.ConnectionIO[(TenantDbId, ApplicationDbId, List[TemplateDbId], List[PermissionDbId])] =
    for {
      tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
      applicationId <- applicationDb
        .insert(applicationEntityWrite_1.copy(tenantId = tenantId))
        .map(_.value.id)

      permissionId_1 <- permissionDb
        .insert(permissionEntityWrite_1.copy(applicationId = applicationId))
        .map(_.value.id)
      permissionId_2 <- permissionDb
        .insert(permissionEntityWrite_2.copy(applicationId = applicationId))
        .map(_.value.id)
      permissionId_3 <- permissionDb
        .insert(permissionEntityWrite_3.copy(applicationId = applicationId))
        .map(_.value.id)

      templateId_1 <- apiKeyTemplateDb
        .insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
        .map(_.value.id)
      templateId_2 <- apiKeyTemplateDb
        .insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
        .map(_.value.id)
      templateId_3 <- apiKeyTemplateDb
        .insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))
        .map(_.value.id)

      templateIds = List(templateId_1, templateId_2, templateId_3)
      permissionIds = List(permissionId_1, permissionId_2, permissionId_3)
    } yield (tenantId, applicationId, templateIds, permissionIds)
}
