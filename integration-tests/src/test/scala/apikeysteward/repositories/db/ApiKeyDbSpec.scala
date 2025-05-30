package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyNotFoundError
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ApiKeyEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.Instant
import java.util.UUID

class ApiKeyDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with FixedClock
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, api_key CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb()

  private val apiKeyDb = new ApiKeyDb()

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    type ApiKeyEntityRaw = (UUID, UUID, String, Instant, Instant)

    val getAllApiKeys: doobie.ConnectionIO[List[ApiKeyEntityRaw]] =
      sql"SELECT * FROM api_key".query[ApiKeyEntityRaw].stream.compile.toList

  }

  "ApiKeyDb on insert" when {

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in
        apiKeyDb
          .insert(apiKeyEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_1)))

      "NOT insert any entity into DB" in {
        val result = for {
          _          <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          resApiKeys <- Queries.getAllApiKeys.transact(transactor)
        } yield resApiKeys

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDb.insert(apiKeyEntityWrite_1.copy(tenantId = tenantDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)

          _   <- apiKeyDb.insert(apiKeyEntityWrite_1.copy(tenantId = tenantDbId_2)).transact(transactor)
          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDb.insert(apiKeyEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyEntityRead_1))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _          <- apiKeyDb.insert(apiKeyEntityWrite_1)
          resApiKeys <- Queries.getAllApiKeys
        } yield resApiKeys).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (apiKeyDbId_1, tenantDbId_1, hashedApiKey_1.value, nowInstant, nowInstant)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }

    "there is a row in the DB with a different API Key" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _        <- apiKeyDb.insert(apiKeyEntityWrite_1)
          inserted <- apiKeyDb.insert(apiKeyEntityWrite_2)
        } yield inserted).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyEntityRead_2))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _          <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _          <- apiKeyDb.insert(apiKeyEntityWrite_2)
          resApiKeys <- Queries.getAllApiKeys
        } yield resApiKeys).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 2

          val expectedApiKeyRow_1 = (apiKeyDbId_1, tenantDbId_1, hashedApiKey_1.value, nowInstant, nowInstant)
          resApiKeys.head shouldBe expectedApiKeyRow_1

          val expectedApiKeyRow_2 = (apiKeyDbId_2, tenantDbId_1, hashedApiKey_2.value, nowInstant, nowInstant)
          resApiKeys(1) shouldBe expectedApiKeyRow_2
        }
      }
    }

    "there is a row in the DB with the same API Key" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _   <- apiKeyDb.insert(apiKeyEntityWrite_1)
          res <- apiKeyDb.insert(apiKeyEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe ApiKeyAlreadyExistsError
          exc.left.value.message shouldBe "API Key already exists."
        }
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)

          _          <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor)
          _          <- apiKeyDb.insert(apiKeyEntityWrite_1).transact(transactor).attempt
          resApiKeys <- Queries.getAllApiKeys.transact(transactor)
        } yield resApiKeys

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (apiKeyDbId_1, tenantDbId_1, hashedApiKey_1.value, nowInstant, nowInstant)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }
  }

  "ApiKeyDb on get" when {

    "there are no Tenants in the DB" should {
      "return empty Option" in
        apiKeyDb
          .getByApiKey(publicTenantId_1, hashedApiKey_1)
          .transact(transactor)
          .asserting(_ shouldBe none[ApiKeyEntity.Read])
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDb.getByApiKey(publicTenantId_1, hashedApiKey_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyEntity.Read])
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _   <- apiKeyDb.insert(apiKeyEntityWrite_1)
          res <- apiKeyDb.getByApiKey(publicTenantId_2, hashedApiKey_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyEntity.Read])
      }
    }

    "there is a row in the DB with different hashed API Key" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _   <- apiKeyDb.insert(apiKeyEntityWrite_1)
          res <- apiKeyDb.getByApiKey(publicTenantId_1, hashedApiKey_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyEntity.Read])
      }
    }

    "there is a row in the DB with the same hashed API Key" should {
      "return Option containing ApiKeyEntity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _   <- apiKeyDb.insert(apiKeyEntityWrite_1)
          res <- apiKeyDb.getByApiKey(publicTenantId_1, hashedApiKey_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyEntityRead_1.copy(id = res.get.id)
        }
      }
    }
  }

  "ApiKeyDb on delete" when {

    "there are no Tenants in the DB" should {

      "return Left containing ApiKeyNotFound" in
        apiKeyDb
          .delete(publicTenantId_1, apiKeyDbId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyNotFoundError))

      "make no changes to the DB" in {
        val result = (for {
          _   <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there are no rows in the DB" should {

      "return Left containing ApiKeyNotFound" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _   <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there is an API Key in the DB for different publicTenantId" should {

      "return Left containing ApiKeyNotFound" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDb.delete(publicTenantId_2, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          _   <- apiKeyDb.delete(publicTenantId_2, apiKeyDbId_1)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (apiKeyDbId_1, tenantDbId_1, hashedApiKey_1.value, nowInstant, nowInstant)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }

    "there is an API Key in the DB with different ID" should {

      "return Left containing ApiKeyNotFound" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          _   <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_2)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (apiKeyDbId_1, tenantDbId_1, hashedApiKey_1.value, nowInstant, nowInstant)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }

    "there is an API Key in the DB with the given ID" should {

      "return Right containing deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          res <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyEntityRead_1))
      }

      "delete this API Key from the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)

          _   <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there are several API Keys in the DB but only one with the given publicKeyId" should {

      "return Right containing deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)

          res <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyEntityRead_1))
      }

      "delete this API Key from the DB and leave others intact" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(apiKeyEntityWrite_2)

          _   <- apiKeyDb.delete(publicTenantId_1, apiKeyDbId_1)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (apiKeyDbId_2, tenantDbId_1, hashedApiKey_2.value, nowInstant, nowInstant)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }
  }

}
