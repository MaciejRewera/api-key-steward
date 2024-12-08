package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2}
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
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
    _ <- sql"TRUNCATE tenant, api_key, api_key_data CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb()
  private val apiKeyDb = new ApiKeyDb()

  private val apiKeyDataDb = new ApiKeyDataDb()

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApiKeysData: doobie.ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList
  }

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(tenantId = tenantDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(tenantId = tenantDbId_2)).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are no API Keys in the DB" should {

      "return Left containing ReferencedApiKeyDoesNotExistError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyDoesNotExistError(apiKeyDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is an API Key in the DB, but with different apiKeyId" should {

      "return Left containing ReferencedApiKeyDoesNotExistError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyDoesNotExistError(apiKeyDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyDataEntityRead_1))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyDbId_1))
        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe ApiKeyIdAlreadyExistsError
          exc.left.value.message shouldBe "API Key Data with the same apiKeyId already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
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
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_1, publicKeyId_1)))
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_1, publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a Tenant in the DB for a different tenantId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_2, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_2, publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1.copy(publicKeyId = publicKeyIdStr_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_1, publicKeyId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.update(publicTenantId_1, apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(updatedEntityRead))
      }

      "update this row" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.getByApiKeyId(publicTenantId_1, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
      "return empty Option" in {
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same userId" should {
      "return Stream containing ApiKeyDataEntity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyDataEntityRead_1))
      }
    }

    "there are several rows in the DB with the same userId together with rows with different userIds" should {
      "return Stream containing all matching ApiKeyDataEntities" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(userId = publicUserId_1))

          _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

          res <- apiKeyDataDb.getByUserId(publicTenantId_1, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(
          _ should contain theSameElementsAs List(
            apiKeyDataEntityRead_1,
            apiKeyDataEntityRead_2.copy(userId = publicUserId_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.getByPublicKeyId(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.getBy(publicTenantId_1, publicUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_1, publicKeyId_1)))
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_1, publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the API Key Data table for different publicTenantId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_2, publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicTenantId_1, publicKeyId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)

          res <- apiKeyDataDb.delete(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyDataEntityRead_1))
      }

      "delete this row from the API Key Data table" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
          _ <- tenantDb.insert(tenantEntityWrite_1)

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
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
