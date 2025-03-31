package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData._
import apikeysteward.base.testdata.ApiKeysPermissionsTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import doobie.implicits._

class TenantRepositoryItSpec extends RepositoryItSpecBase {

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

  private val resourceServerRepository =
    new ResourceServerRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)(
      transactor
    )

  private val apiKeyTemplateRepository =
    new ApiKeyTemplateRepository(
      uuidGenerator,
      tenantDb,
      apiKeyTemplateDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeyTemplatesUsersDb
    )(transactor)

  private val secureHashGenerator = new SecureHashGenerator(Algorithm.SHA3_256)

  private val apiKeyRepository = new ApiKeyRepository(
    uuidGenerator,
    secureHashGenerator,
    tenantDb,
    apiKeyDb,
    apiKeyDataDb,
    permissionDb,
    userDb,
    apiKeyTemplateDb,
    apiKeysPermissionsDb
  )(transactor)

  private val userRepository =
    new UserRepository(uuidGenerator, tenantDb, userDb, apiKeyTemplatesUsersDb, apiKeyRepository)(transactor)

  private val repository =
    new TenantRepository(uuidGenerator, tenantDb, resourceServerRepository, apiKeyTemplateRepository, userRepository)(
      transactor
    )

  "TenantRepository on delete" when {

    "given Tenant is active" should {

      "NOT delete associations between Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

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

      "NOT delete associations between Users and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplatesUsersAssociations.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1.toRead,
            apiKeyTemplatesUsersEntityWrite_2_1.toRead,
            apiKeyTemplatesUsersEntityWrite_2_2.toRead
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

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

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

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

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

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

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(permissionEntityRead_1, permissionEntityRead_2, permissionEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ResourceServers" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(resourceServerEntityRead_1))
      }

      "NOT delete related ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

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

      "NOT delete related Users" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            userEntityRead_1.copy(tenantId = tenantDbId_1),
            userEntityRead_2.copy(tenantId = tenantDbId_1),
            userEntityRead_3.copy(tenantId = tenantDbId_1)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this Tenant" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(_ should contain theSameElementsAs List(tenantEntityRead_1))
      }
    }

    "there are entities in the DB, related to given inactive Tenant" should {

      "delete associations between Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplatesPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete associations between Users and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplatesUsersAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }

      "delete associations between Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }

      "delete ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
      }

      "delete all ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }

      "delete Permissions" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "delete ResourceServers" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }

      "delete ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }

      "delete Users" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }

      "delete the Tenant" in {
        val result = for {
          _ <- insertPrerequisiteDataAll()

          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }
  }

}
