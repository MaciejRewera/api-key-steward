package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeysTestData.publicKeyId_1
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import doobie.ConnectionIO
import doobie.implicits._

class ApiKeyRepositoryItSpec extends RepositoryItSpecBase {

  import doobie.postgres._
  import doobie.postgres.implicits._

  private val uuidGenerator       = new UuidGenerator
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

  private def getApiKeys(publicKeyId: ApiKeyId): ConnectionIO[List[ApiKeyEntity.Read]] =
    sql"""SELECT
         |  api_key.id,
         |  api_key.tenant_id,
         |  api_key.created_at,
         |  api_key.updated_at
         |FROM api_key
         |JOIN api_key_data ON api_key_data.api_key_id = api_key.id
         |WHERE api_key_data.public_key_id = ${publicKeyId.toString}
         |""".stripMargin.query[ApiKeyEntity.Read].stream.compile.toList

  private def getApiKeyData(publicKeyId: ApiKeyId): ConnectionIO[List[ApiKeyDataEntity.Read]] =
    sql"SELECT * FROM api_key_data WHERE public_key_id = ${publicKeyId.toString}"
      .query[ApiKeyDataEntity.Read]
      .stream
      .compile
      .toList

  private def getApiKeysPermissionsAssociations(
      publicKeyId: ApiKeyId
  ): ConnectionIO[List[ApiKeysPermissionsEntity.Read]] =
    sql"""SELECT *
         |FROM api_keys_permissions
         |JOIN api_key_data ON api_key_data.id = api_keys_permissions.api_key_data_id
         |WHERE api_key_data.public_key_id = ${publicKeyId.toString}
         |""".stripMargin.query[ApiKeysPermissionsEntity.Read].stream.compile.toList

  "ApiKeyRepository on delete" should {

    "delete associations between Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _   <- repository.delete(publicTenantId_1, publicKeyId_1)
        res <- getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyData(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _   <- repository.delete(publicTenantId_1, publicKeyId_1)
        res <- getApiKeyData(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
    }

    "delete ApiKeys" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeys(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _   <- repository.delete(publicTenantId_1, publicKeyId_1)
        res <- getApiKeys(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
    }
  }

  "ApiKeyRepository on deleteAllForUserOp" should {

    "delete associations between Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _   <- repository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(transactor)
        res <- getApiKeysPermissionsAssociations(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyData(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _   <- repository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(transactor)
        res <- getApiKeyData(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
    }

    "delete ApiKeys" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeys(publicKeyId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _   <- repository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(transactor)
        res <- getApiKeys(publicKeyId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
    }
  }

}
