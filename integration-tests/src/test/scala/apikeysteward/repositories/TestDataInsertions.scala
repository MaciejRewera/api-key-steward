package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.ResourceServersTestData.resourceServerEntityWrite_1
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantEntityWrite_1
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.repositories.db._
import org.scalatest.EitherValues

import java.util.UUID

private[repositories] object TestDataInsertions extends EitherValues {

  type TenantDbId = UUID
  type ResourceServerDbId = UUID
  type PermissionDbId = UUID
  type TemplateDbId = UUID
  type UserDbId = UUID

  def insertPrerequisiteTemplatesAndPermissions(
      tenantDb: TenantDb,
      resourceServerDb: ResourceServerDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb
  ): doobie.ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[PermissionDbId])] =
    for {
      tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
      resourceServerId <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

      _ <- permissionDb.insert(permissionEntityWrite_1.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_2.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_3.copy(resourceServerId = resourceServerId))

      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

      templateIds = List(templateDbId_1, templateDbId_2, templateDbId_3)
      permissionIds = List(permissionDbId_1, permissionDbId_2, permissionDbId_3)
    } yield (tenantId, resourceServerId, templateIds, permissionIds)

  def insertPrerequisiteTemplatesAndUsers(
      tenantDb: TenantDb,
      userDb: UserDb,
      apiKeyTemplateDb: ApiKeyTemplateDb
  ): doobie.ConnectionIO[(TenantDbId, List[TemplateDbId], List[UserDbId])] =
    for {
      tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

      _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
      _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId))
      _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId))

      templateIds = List(templateDbId_1, templateDbId_2, templateDbId_3)
      userIds = List(userDbId_1, userDbId_2, userDbId_3)
    } yield (tenantId, templateIds, userIds)

  def insertPrerequisiteTemplatesAndUsersAndPermissions(
      tenantDb: TenantDb,
      userDb: UserDb,
      resourceServerDb: ResourceServerDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb
  ): doobie.ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[UserDbId], List[PermissionDbId])] =
    for {
      tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
      resourceServerId <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

      _ <- permissionDb.insert(permissionEntityWrite_1.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_2.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_3.copy(resourceServerId = resourceServerId))

      _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
      _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId))
      _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId))

      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

      templateIds = List(templateDbId_1, templateDbId_2, templateDbId_3)
      userIds = List(userDbId_1, userDbId_2, userDbId_3)
      permissionIds = List(permissionDbId_1, permissionDbId_2, permissionDbId_3)
    } yield (tenantId, resourceServerId, templateIds, userIds, permissionIds)

  def insertPrerequisiteTemplatesAndUsersAndPermissionsAndApiKeys(
      tenantDb: TenantDb,
      userDb: UserDb,
      resourceServerDb: ResourceServerDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb,
      apiKeyDb: ApiKeyDb,
      apiKeyDataDb: ApiKeyDataDb
  ): doobie.ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[UserDbId], List[PermissionDbId])] =
    for {
      tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
      resourceServerId <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

      _ <- permissionDb.insert(permissionEntityWrite_1.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_2.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_3.copy(resourceServerId = resourceServerId))

      _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
      _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId))
      _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId))

      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

      _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
      _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)
      _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

      templateIds = List(templateDbId_1, templateDbId_2, templateDbId_3)
      userIds = List(userDbId_1, userDbId_2, userDbId_3)
      permissionIds = List(permissionDbId_1, permissionDbId_2, permissionDbId_3)
    } yield (tenantId, resourceServerId, templateIds, userIds, permissionIds)

}
