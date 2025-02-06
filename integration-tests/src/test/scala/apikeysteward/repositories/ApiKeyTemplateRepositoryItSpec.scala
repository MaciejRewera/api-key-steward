package apikeysteward.repositories

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeysPermissionsTestData.{
  apiKeysPermissionsEntityWriteToRead,
  apiKeysPermissionsEntityWrite_1_1,
  apiKeysPermissionsEntityWrite_1_2,
  apiKeysPermissionsEntityWrite_1_3,
  apiKeysPermissionsEntityWrite_2_1,
  apiKeysPermissionsEntityWrite_3_2,
  apiKeysPermissionsEntityWrite_3_3
}
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import doobie.implicits._

class ApiKeyTemplateRepositoryItSpec extends RepositoryItSpecBase {

  import doobie.postgres._
  import doobie.postgres.implicits._

  private val uuidGenerator = new UuidGenerator

  private val repository = new ApiKeyTemplateRepository(
    uuidGenerator,
    tenantDb,
    apiKeyTemplateDb,
    permissionDb,
    apiKeyTemplatesPermissionsDb,
    apiKeyTemplatesUsersDb
  )(transactor)

  private def getApiKeyTemplatesUsersAssociations(
      publicTemplateId: ApiKeyTemplateId
  ): IO[List[ApiKeyTemplatesUsersEntity.Read]] =
    sql"""SELECT *
         |FROM api_key_templates_users
         |JOIN api_key_template ON api_key_template.id = api_key_templates_users.api_key_template_id
         |WHERE api_key_template.public_template_id = ${publicTemplateId.toString}
         |""".stripMargin.query[ApiKeyTemplatesUsersEntity.Read].stream.compile.toList.transact(transactor)

  private def getApiKeyTemplatesPermissionsAssociations(
      publicTemplateId: ApiKeyTemplateId
  ): IO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
    sql"""SELECT *
         |FROM api_key_templates_permissions
         |JOIN api_key_template ON api_key_template.id = api_key_templates_permissions.api_key_template_id
         |WHERE api_key_template.public_template_id = ${publicTemplateId.toString}
         |""".stripMargin.query[ApiKeyTemplatesPermissionsEntity.Read].stream.compile.toList.transact(transactor)

  "ApiKeyTemplateRepository on delete" should {

    "delete associations between ApiKeyTemplate and Users" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyTemplatesUsersAssociations(publicTemplateId_1)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicTemplateId_1)
        res <- getApiKeyTemplatesUsersAssociations(publicTemplateId_1)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
    }

    "delete associations between ApiKeyTemplate and Permissions" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- getApiKeyTemplatesPermissionsAssociations(publicTemplateId_1)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicTemplateId_1)
        res <- getApiKeyTemplatesPermissionsAssociations(publicTemplateId_1)
      } yield res

      result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
    }

    "NOT delete associations between Permissions and ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicTemplateId_1)
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

    "NOT delete ApiKeyData" in {
      val result = for {
        _ <- insertPrerequisiteDataAll()

        entitiesBeforeDeletion <- Queries.getAllApiKeyData.transact(transactor)
        _ = entitiesBeforeDeletion should not be empty

        _ <- repository.delete(publicTenantId_1, publicTemplateId_1)
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

        _ <- repository.delete(publicTenantId_1, publicTemplateId_1)
        res <- Queries.getAllApiKeys.transact(transactor)
      } yield res

      result.asserting { res =>
        val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

        res should contain theSameElementsAs expectedEntities
      }
    }
  }

}
