package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.publicResourceServerId_1
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1}
import apikeysteward.model.Permission.PermissionId
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import doobie.implicits._

class PermissionRepositoryItSpec extends RepositoryItSpecBase {

  import doobie.postgres._
  import doobie.postgres.implicits._

  private val uuidGenerator = new UuidGenerator

  private val repository = new PermissionRepository(
    uuidGenerator,
    tenantDb,
    resourceServerDb,
    permissionDb,
    apiKeyTemplatesPermissionsDb,
    apiKeysPermissionsDb
  )(transactor)

  private def getApiKeysPermissionsAssociations(
      publicPermissionId: PermissionId
  ): IO[List[ApiKeysPermissionsEntity.Read]] =
    sql"""SELECT *
         |FROM api_keys_permissions
         |JOIN permission ON permission.id = api_keys_permissions.permission_id
         |WHERE permission.public_permission_id = ${publicPermissionId.toString}
         |""".stripMargin.query[ApiKeysPermissionsEntity.Read].stream.compile.toList.transact(transactor)

  private def getApiKeyTemplatesPermissionsAssociations(
      publicPermissionId: PermissionId
  ): IO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
    sql"""SELECT *
         |FROM api_key_templates_permissions
         |JOIN permission ON permission.id = api_key_templates_permissions.permission_id
         |WHERE permission.public_permission_id = ${publicPermissionId.toString}
         |""".stripMargin.query[ApiKeyTemplatesPermissionsEntity.Read].stream.compile.toList.transact(transactor)

  private def getPermission(publicPermissionId: PermissionId): IO[Option[PermissionEntity.Read]] =
    sql"""SELECT *
         |FROM permission
         |WHERE permission.public_permission_id = ${publicPermissionId.toString}
         |""".stripMargin.query[PermissionEntity.Read].option.transact(transactor)

  "PermissionRepository on delete" should {

    "delete associations between Permission and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeysPermissionsAssociations(publicPermissionId_1)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        res <- getApiKeysPermissionsAssociations(publicPermissionId_1)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete associations between Permission and ApiKeyTemplates" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyTemplatesPermissionsAssociations(publicPermissionId_1)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        res <- getApiKeyTemplatesPermissionsAssociations(publicPermissionId_1)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
    }

    "delete Permission" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getPermission(publicPermissionId_1)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        res <- getPermission(publicPermissionId_1)
      } yield res

      result.asserting(_ shouldBe Option.empty[PermissionEntity.Read])
    }

    "NOT delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getAllApiKeyData.transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        res <- Queries.getAllApiKeyData.transact(transactor)
      } yield res

      result.asserting { res =>
        val expectedEntities = List(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2, apiKeyDataEntityRead_3)

        res should contain theSameElementsAs expectedEntities
      }
    }

    "NOT delete ApiKey" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getAllApiKeys.transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        res <- Queries.getAllApiKeys.transact(transactor)
      } yield res

      result.asserting { res =>
        val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

        res should contain theSameElementsAs expectedEntities
      }
    }

    "NOT delete ApiKeyTemplate" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getAllApiKeyTemplates.transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        res <- Queries.getAllApiKeyTemplates.transact(transactor)
      } yield res

      result.asserting { res =>
        val expectedEntities = List(
          apiKeyTemplateEntityRead_1.copy(tenantId = tenantDbId_1),
          apiKeyTemplateEntityRead_2.copy(tenantId = tenantDbId_1),
          apiKeyTemplateEntityRead_3.copy(tenantId = tenantDbId_1)
        )

        res should contain theSameElementsAs expectedEntities
      }
    }
  }

}
