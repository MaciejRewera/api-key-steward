package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData._
import apikeysteward.base.testdata.ApiKeysPermissionsTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.resourceServerEntityWrite_1
import apikeysteward.base.testdata.TenantsTestData.{tenantDbId_1, tenantEntityWrite_1}
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.repositories.db._
import org.scalatest.EitherValues

import java.util.UUID

private[repositories] object TestDataInsertions extends EitherValues {

  type TenantDbId         = UUID
  type ResourceServerDbId = UUID
  type PermissionDbId     = UUID
  type TemplateDbId       = UUID
  type UserDbId           = UUID

  def insertPrerequisiteTemplatesAndPermissions(
      tenantDb: TenantDb,
      resourceServerDb: ResourceServerDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb
  ): doobie.ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[PermissionDbId])] =
    for {
      tenantId         <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
      resourceServerId <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

      _ <- permissionDb.insert(permissionEntityWrite_1.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_2.copy(resourceServerId = resourceServerId))
      _ <- permissionDb.insert(permissionEntityWrite_3.copy(resourceServerId = resourceServerId))

      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

      templateIds   = List(templateDbId_1, templateDbId_2, templateDbId_3)
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
      userIds     = List(userDbId_1, userDbId_2, userDbId_3)
    } yield (tenantId, templateIds, userIds)

  def insertPrerequisiteTemplatesAndUsersAndPermissions(
      tenantDb: TenantDb,
      userDb: UserDb,
      resourceServerDb: ResourceServerDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb
  ): doobie.ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[UserDbId], List[PermissionDbId])] =
    for {
      tenantId         <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
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

      templateIds   = List(templateDbId_1, templateDbId_2, templateDbId_3)
      userIds       = List(userDbId_1, userDbId_2, userDbId_3)
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
      tenantId         <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
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

      templateIds   = List(templateDbId_1, templateDbId_2, templateDbId_3)
      userIds       = List(userDbId_1, userDbId_2, userDbId_3)
      permissionIds = List(permissionDbId_1, permissionDbId_2, permissionDbId_3)
    } yield (tenantId, resourceServerId, templateIds, userIds, permissionIds)

  def insertAll(
      tenantDb: TenantDb,
      userDb: UserDb,
      resourceServerDb: ResourceServerDb,
      permissionDb: PermissionDb,
      apiKeyTemplateDb: ApiKeyTemplateDb,
      apiKeyDb: ApiKeyDb,
      apiKeyDataDb: ApiKeyDataDb,
      apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb,
      apiKeyTemplatesUsersDb: ApiKeyTemplatesUsersDb,
      apiKeysPermissionsDb: ApiKeysPermissionsDb
  ): doobie.ConnectionIO[Unit] =
    for {
      _ <- tenantDb.insert(tenantEntityWrite_1)
      _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

      _ <- permissionDb.insert(permissionEntityWrite_1)
      _ <- permissionDb.insert(permissionEntityWrite_2)
      _ <- permissionDb.insert(permissionEntityWrite_3)

      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantDbId_1))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantDbId_1))
      _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantDbId_1))

      associationEntities = List(
        apiKeyTemplatesPermissionsEntityWrite_1_1,
        apiKeyTemplatesPermissionsEntityWrite_2_1,
        apiKeyTemplatesPermissionsEntityWrite_2_2
      )
      _ <- apiKeyTemplatesPermissionsDb.insertMany(associationEntities)

      _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantDbId_1)).map(_.value.id)
      _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantDbId_1)).map(_.value.id)
      _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantDbId_1)).map(_.value.id)

      associationEntities = List(
        apiKeyTemplatesUsersEntityWrite_1_1,
        apiKeyTemplatesUsersEntityWrite_2_1,
        apiKeyTemplatesUsersEntityWrite_2_2
      )
      _ <- apiKeyTemplatesUsersDb.insertMany(associationEntities)

      _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
      _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)
      _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

      associationEntities = List(
        apiKeysPermissionsEntityWrite_1_1,
        apiKeysPermissionsEntityWrite_1_2,
        apiKeysPermissionsEntityWrite_1_3,
        apiKeysPermissionsEntityWrite_2_1,
        apiKeysPermissionsEntityWrite_3_2,
        apiKeysPermissionsEntityWrite_3_3
      )
      _ <- apiKeysPermissionsDb.insertMany(associationEntities)
    } yield ()

}
