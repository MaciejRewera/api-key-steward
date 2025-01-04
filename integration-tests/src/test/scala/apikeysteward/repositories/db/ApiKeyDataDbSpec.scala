package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{templateDbId_1, templateDbId_4}
import apikeysteward.base.testdata.ApiKeysTestData
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, userDbId_1, userDbId_4}
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.ApiKeyDbError.{
  ApiKeyDataNotFoundError,
  ReferencedApiKeyTemplateDoesNotExistError,
  ReferencedUserDoesNotExistError
}
import apikeysteward.repositories.TestDataInsertions.{
  PermissionDbId,
  ResourceServerDbId,
  TemplateDbId,
  TenantDbId,
  UserDbId
}
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyDataDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <-
      sql"TRUNCATE tenant, tenant_user, resource_server, permission, api_key_template, api_key, api_key_data CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb()
  private val resourceServerDb = new ResourceServerDb
  private val permissionDb = new PermissionDb
  private val userDb = new UserDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val apiKeyDb = new ApiKeyDb

  private val apiKeyDataDb = new ApiKeyDataDb()

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApiKeysData: doobie.ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList

    val deleteAllUsers: doobie.ConnectionIO[Int] =
      sql"DELETE FROM tenant_user".update.run

    val deleteAllTemplates: doobie.ConnectionIO[Int] =
      sql"DELETE FROM api_key_template".update.run
  }

  private def insertPrerequisiteData()
      : ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[UserDbId], List[PermissionDbId])] =
    TestDataInsertions.insertPrerequisiteTemplatesAndUsersAndPermissions(
      tenantDb,
      userDb,
      resourceServerDb,
      permissionDb,
      apiKeyTemplateDb
    )

  "ApiKeyDataDb on insert" when {

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        apiKeyDataDb
          .insert(apiKeyDataEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_1)))
      }

      "NOT insert any entity into DB" in {
        val result = for {
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          resApiKeys <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeys

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(tenantId = tenantDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(tenantId = tenantDbId_2)).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no Users in the DB" should {

      "return Left containing ReferencedUserDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- Queries.deleteAllUsers

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedUserDoesNotExistError.fromDbId(userDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          _ <- Queries.deleteAllUsers.transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a User in the DB, but with different userId" should {

      "return Left containing ReferencedUserDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(userId = userDbId_4))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedUserDoesNotExistError.fromDbId(userDbId_4)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(userId = userDbId_4)).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no ApiKeyTemplates in the DB" should {

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- Queries.deleteAllTemplates

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError.fromDbId(templateDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          _ <- Queries.deleteAllTemplates.transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB, but with different templateId" should {

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- Queries.deleteAllTemplates

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(templateId = templateDbId_4))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError.fromDbId(templateDbId_4)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          _ <- Queries.deleteAllTemplates.transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(templateId = templateDbId_4)).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no API Keys in the DB" should {

      "return Left containing ReferencedApiKeyDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyDoesNotExistError(apiKeyDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is an API Key in the DB, but with different apiKeyId" should {

      "return Left containing ReferencedApiKeyDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyDoesNotExistError(apiKeyDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyDbId_2)).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyDataEntityRead_1))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
          resApiKeysData <- Queries.getAllApiKeysData
        } yield resApiKeysData).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there is a row in the DB with the same data, but different both apiKeyId and publicKeyId" should {

      "return inserted entity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
          inserted <- apiKeyDataDb.insert(
            apiKeyDataEntityWrite_1.copy(id = apiKeyDataDbId_2, apiKeyId = apiKeyDbId_2, publicKeyId = publicKeyIdStr_2)
          )
        } yield inserted).transact(transactor)

        result.asserting {
          _ shouldBe Right(
            apiKeyDataEntityRead_1.copy(
              id = apiKeyDataDbId_2,
              apiKeyId = apiKeyDbId_2,
              publicKeyId = publicKeyIdStr_2
            )
          )
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
          _ <- apiKeyDataDb.insert(
            apiKeyDataEntityWrite_1.copy(id = apiKeyDataDbId_2, apiKeyId = apiKeyDbId_2, publicKeyId = publicKeyIdStr_2)
          )
          resApiKeysData <- Queries.getAllApiKeysData
        } yield resApiKeysData).transact(transactor)

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 2

          resApiKeysData should contain theSameElementsAs List(
            apiKeyDataEntityRead_1,
            apiKeyDataEntityRead_1.copy(id = apiKeyDataDbId_2, apiKeyId = apiKeyDbId_2, publicKeyId = publicKeyIdStr_2)
          )
        }
      }
    }

    "there is a row in the DB with the same apiKeyId" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyDbId_1))
        } yield res).transact(transactor)

        result.asserting { exc =>
          exc shouldBe Left(ApiKeyIdAlreadyExistsError)
          exc.left.value.message shouldBe "API Key Data with the same apiKeyId already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyDbId_1)).transact(transactor)

          resApiKeysData <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeysData

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there is a row in the DB with the same publicKeyId" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(publicKeyId = publicKeyIdStr_1))

        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe PublicKeyIdAlreadyExistsError
          exc.left.value.message shouldBe "API Key Data with the same publicKeyId already exists."
        }
      }

      "NOT insert the second entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_2).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(publicKeyId = publicKeyIdStr_1)).transact(transactor)

          resApiKeysData <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeysData

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }
  }

  "ApiKeyDataDb on update" when {

    val updatedEntityRead = apiKeyDataEntityRead_1.copy(
      name = ApiKeysTestData.nameUpdated,
      description = ApiKeysTestData.descriptionUpdated,
      updatedAt = nowInstant
    )

    "there is no Tenant in the DB" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb
          .update(publicTenantId_1, apiKeyDataEntityUpdate_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)

          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no rows in the API Key Data table" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a Tenant in the DB for a different tenantId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_2, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDataDb.update(publicTenantId_2, apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there is a row in the API Key Data table with different publicKeyId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1.copy(publicKeyId = publicKeyIdStr_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1.copy(publicKeyId = publicKeyIdStr_2))
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there is a row in the API Key Data table with given userId and publicKeyId" should {

      "return Right containing updated entity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(updatedEntityRead))
      }

      "update this row" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(updatedEntityRead))
      }
    }

    "there are several rows in the API Key Data table but only one with given userId and publicKeyId" should {

      "return Right containing updated entity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(updatedEntityRead))
      }

      "update only this row and leave others unchanged" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          _ <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(
          _ should contain theSameElementsAs List(updatedEntityRead, apiKeyDataEntityRead_2, apiKeyDataEntityRead_3)
        )
      }
    }
  }

  "ApiKeyDataDb on getByApiKeyId" when {

    "there are no Tenants in the DB" should {
      "return empty Option" in {
        apiKeyDataDb.getByApiKeyId(publicTenantId_1, apiKeyDbId_1).transact(transactor).asserting(_ shouldBe None)
      }
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.getByApiKeyId(publicTenantId_1, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByApiKeyId(publicTenantId_2, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different apiKeyId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByApiKeyId(publicTenantId_1, apiKeyDbId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same apiKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByApiKeyId(publicTenantId_1, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Some(apiKeyDataEntityRead_1))
      }
    }
  }

  "ApiKeyDataDb on getByUserId" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        apiKeyDataDb
          .getByUserId(publicTenantId_1, publicUserId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no rows in the DB" should {
      "return empty Stream" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByUserId(publicTenantId_2, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with different userId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same userId" should {
      "return Stream containing ApiKeyDataEntity" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_1).compile.toList.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there are several rows in the DB with the same userId together with rows with different userIds" should {
      "return Stream containing all matching ApiKeyDataEntities" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(userId = userDbId_1))

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(
          _ should contain theSameElementsAs List(
            apiKeyDataEntityRead_1,
            apiKeyDataEntityRead_2.copy(userId = userDbId_1)
          )
        )
      }
    }
  }

  "ApiKeyDataDb on getByPublicKeyId" when {

    "there are no Tenants in the DB" should {
      "return empty Option" in {
        apiKeyDataDb
          .getByPublicKeyId(publicTenantId_1, publicKeyId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.getByPublicKeyId(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByPublicKeyId(publicTenantId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with different publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByPublicKeyId(publicTenantId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same publicKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByPublicKeyId(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Some(apiKeyDataEntityRead_1))
      }
    }

    "there are several rows in the DB" should {
      "return Option containing ApiKeyDataEntity with the same publicKeyId" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          res <- apiKeyDataDb.getByPublicKeyId(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Some(apiKeyDataEntityRead_1))
      }
    }
  }

  "ApiKeyDataDb on getBy(:userId, :publicKeyId)" when {

    "there are no Tenants in the DB" should {
      "return empty Option" in {
        apiKeyDataDb
          .getBy(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .transact(transactor)
          .asserting(_ shouldBe None)
      }
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.getBy(publicTenantId_1, publicUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getBy(publicTenantId_2, publicUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different both userId and publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getBy(publicTenantId_1, publicUserId_2, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same userId but different publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getBy(publicTenantId_1, publicUserId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same publicKeyId but different userId" should {
      "return empty Option" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getBy(publicTenantId_1, publicUserId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same both userId and publicKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getBy(publicTenantId_1, publicUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = res.get.apiKeyId)
        }
      }
    }
  }

  "ApiKeyDataDb on delete(:publicKeyId)" when {

    "there are no Tenants in the DB" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb
          .delete(publicTenantId_1, publicKeyId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no rows in the API Key Data table" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the API Key Data table for different publicTenantId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDataDb.delete(publicTenantId_2, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there is a row in the API Key Data table with different publicKeyId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_2)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there is a row in the API Key Data table with given publicKeyId" should {

      "return Right containing deleted entity" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyDataEntityRead_1))
      }

      "delete this row from the API Key Data table" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are several rows in the API Key Data table but only one with given publicKeyId" should {

      "return Right containing deleted entity" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyDataEntityRead_1))
      }

      "delete this row from the API Key Data table and leave others intact" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          _ <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ should contain theSameElementsAs List(apiKeyDataEntityRead_2, apiKeyDataEntityRead_3))
      }
    }
  }
}
