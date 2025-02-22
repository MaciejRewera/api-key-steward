package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeysPermissionsTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1, tenantEntityRead_1}
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import doobie.implicits._

class ResourceServerRepositoryItSpec extends RepositoryItSpecBase {

  private val uuidGenerator = new UuidGenerator

  private val permissionRepository =
    new PermissionRepository(
      uuidGenerator,
      tenantDb,
      resourceServerDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeysPermissionsDb
    )(transactor)

  private val repository =
    new ResourceServerRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)(
      transactor
    )

  "ResourceServerRepository on delete" should {

    "delete associations between related Permissions and ApiKeyTemplates" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllApiKeyTemplatesPermissionsAssociations.transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
    }

    "delete associations between related Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
      } yield res

      result.asserting(_ should contain theSameElementsAs List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete related Permissions" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllPermissions.transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
    }

    "delete this ResourceServer" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllResourceServers.transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
    }

    "NOT delete ApiKeyTemplates" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

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

    "NOT delete ApiKeys" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllApiKeys.transact(transactor)
      } yield res

      result.asserting { res =>
        val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

        res should contain theSameElementsAs expectedEntities
      }
    }

    "NOT delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllApiKeyData.transact(transactor)
      } yield res

      result.asserting { res =>
        val expectedEntities = List(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2, apiKeyDataEntityRead_3)

        res should contain theSameElementsAs expectedEntities
      }
    }

    "NOT delete the Tenant" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

        res <- Queries.getAllTenants.transact(transactor)
      } yield res

      result.asserting(_ shouldBe List(tenantEntityRead_1))
    }
  }

}
