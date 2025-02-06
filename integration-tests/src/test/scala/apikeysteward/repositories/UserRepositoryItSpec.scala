package apikeysteward.repositories

import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.model.User.UserId
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import apikeysteward.repositories.db.entity.{
  ApiKeyDataEntity,
  ApiKeyEntity,
  ApiKeyTemplatesUsersEntity,
  ApiKeysPermissionsEntity
}
import apikeysteward.services.UuidGenerator
import doobie.ConnectionIO
import doobie.implicits._

class UserRepositoryItSpec extends RepositoryItSpecBase {

  import doobie.postgres._
  import doobie.postgres.implicits._

  private val uuidGenerator = new UuidGenerator
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

  private val repository = new UserRepository(
    uuidGenerator,
    tenantDb,
    userDb,
    apiKeyTemplatesUsersDb,
    apiKeyRepository
  )(transactor)

  private def getApiKeyTemplatesUsersAssociations(
      publicUserId: UserId
  ): ConnectionIO[List[ApiKeyTemplatesUsersEntity.Read]] =
    sql"""SELECT *
         |FROM api_key_templates_users
         |JOIN tenant_user ON tenant_user.id = api_key_templates_users.user_id
         |WHERE tenant_user.public_user_id = ${publicUserId.toString}
         |""".stripMargin.query[ApiKeyTemplatesUsersEntity.Read].stream.compile.toList

  private def getApiKeysPermissionsAssociations(
      publicUserId: UserId
  ): ConnectionIO[List[ApiKeysPermissionsEntity.Read]] =
    sql"""SELECT *
         |FROM api_keys_permissions
         |JOIN api_key_data ON api_key_data.id = api_keys_permissions.api_key_data_id
         |JOIN tenant_user ON tenant_user.id = api_key_data.user_id
         |WHERE tenant_user.public_user_id = ${publicUserId.toString}
         |""".stripMargin.query[ApiKeysPermissionsEntity.Read].stream.compile.toList

  private def getApiKeys(publicUserId: UserId): ConnectionIO[List[ApiKeyEntity.Read]] =
    sql"""SELECT
         |  api_key.id,
         |  api_key.tenant_id,
         |  api_key.created_at,
         |  api_key.updated_at
         |FROM api_key
         |JOIN api_key_data ON api_key_data.api_key_id = api_key.id
         |JOIN tenant_user ON tenant_user.id = api_key_data.user_id
         |WHERE tenant_user.public_user_id = ${publicUserId.toString}
         |""".stripMargin.query[ApiKeyEntity.Read].stream.compile.toList

  private def getApiKeyData(publicUserId: UserId): ConnectionIO[List[ApiKeyDataEntity.Read]] =
    sql"""
         |SELECT *
         |FROM api_key_data
         |JOIN tenant_user ON tenant_user.id = api_key_data.user_id
         |WHERE tenant_user.public_user_id = ${publicUserId.toString}
         |""".stripMargin.query[ApiKeyDataEntity.Read].stream.compile.toList

  "UserRepository on delete" should {

    "delete associations between Users and ApiKeyTemplates" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyTemplatesUsersAssociations(publicUserId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicUserId_1)
        res <- getApiKeyTemplatesUsersAssociations(publicUserId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete associations between Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeysPermissionsAssociations(publicUserId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicUserId_1)
        res <- getApiKeysPermissionsAssociations(publicUserId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
    }

    "delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyData(publicUserId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicUserId_1)
        res <- getApiKeyData(publicUserId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
    }

    "delete ApiKeys" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeys(publicUserId_1).transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicUserId_1)
        res <- getApiKeys(publicUserId_1).transact(transactor)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
    }
  }

}
