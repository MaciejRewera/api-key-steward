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

  "ResourceServerRepository on delete" when {

    "given ResourceServer is active" should {

      "NOT delete associations between related Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyTemplatesPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_2.toRead
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between related Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeysPermissionsEntityWrite_1_1.toRead,
            apiKeysPermissionsEntityWrite_1_2.toRead,
            apiKeysPermissionsEntityWrite_1_3.toRead,
            apiKeysPermissionsEntityWrite_2_1.toRead,
            apiKeysPermissionsEntityWrite_3_2.toRead,
            apiKeysPermissionsEntityWrite_3_3.toRead
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2, apiKeyDataEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(permissionEntityRead_1, permissionEntityRead_2, permissionEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this ResourceServer" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting { res =>
          res shouldBe List(resourceServerEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there are entities in the DB for given inactive ResourceServer" should {

      "delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyTemplatesPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete associations between related Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting(_ should contain theSameElementsAs List.empty[ApiKeysPermissionsEntity.Read])
      }

      "delete these Permissions" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "delete this ResourceServer" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }

      "NOT delete ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
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

      "NOT delete related ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
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

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(tenantEntityRead_1))
      }
    }
  }

}
