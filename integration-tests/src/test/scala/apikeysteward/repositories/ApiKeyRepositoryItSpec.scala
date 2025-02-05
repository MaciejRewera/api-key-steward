package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeysTestData.publicKeyId_1
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import doobie.implicits._

class ApiKeyRepositoryItSpec extends RepositoryItSpecBase {

  private val uuidGenerator = new UuidGenerator
  private val secureHashGenerator = new SecureHashGenerator(Algorithm.SHA3_256)

  private val repository = new ApiKeyRepository(
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

  "ApiKeyRepository on delete" should {

    "delete associations between Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicKeyId_1)
        res <- Queries.getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getApiKeyData(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicKeyId_1)
        res <- Queries.getApiKeyData(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
    }

    "delete ApiKeys" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getApiKeys(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicKeyId_1)
        res <- Queries.getApiKeys(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
    }
  }

  "ApiKeyRepository on deleteAllForUserOp" should {

    "delete associations between Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(transactor)
        res <- Queries.getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getApiKeyData(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(transactor)
        res <- Queries.getApiKeyData(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
    }

    "delete ApiKeys" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getApiKeys(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(transactor)
        res <- Queries.getApiKeys(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
    }
  }

}
