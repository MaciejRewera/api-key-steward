package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.ApiKeyAlreadyExistsError
import apikeysteward.repositories.db.entity.ApiKeyEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.Instant

class ApiKeyDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with FixedClock
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE api_key CASCADE".update.run
  } yield ()

  private val apiKeyDb = new ApiKeyDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    type ApiKeyEntityRaw = (Long, String, Instant, Instant)

    val getAllApiKeys =
      sql"SELECT * FROM api_key".query[ApiKeyEntityRaw].stream.compile.toList
  }

  private val testApiKey_1 = "test-api-key-1"
  private val testApiKey_2 = "test-api-key-2"

  private val testApiKeyEntityWrite_1 = ApiKeyEntity.Write(testApiKey_1)
  private val testApiKeyEntityWrite_2 = ApiKeyEntity.Write(testApiKey_2)

  private val testApiKeyEntityRead_1 = ApiKeyEntity.Read(id = 1L, createdAt = now, updatedAt = now)
  private val testApiKeyEntityRead_2 = ApiKeyEntity.Read(id = 2L, createdAt = now, updatedAt = now)

  "ApiKeyDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = apiKeyDb.insert(testApiKeyEntityWrite_1).transact(transactor)

        result.asserting { res =>
          res.isRight shouldBe true
          res.value shouldBe testApiKeyEntityRead_1.copy(id = res.value.id)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          resApiKeys <- Queries.getAllApiKeys
        } yield resApiKeys).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (resApiKeys.head._1, testApiKey_1, now, now)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }

    "there is a different API Key in the DB" should {

      "return inserted entity" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          inserted <- apiKeyDb.insert(testApiKeyEntityWrite_2)
        } yield inserted).transact(transactor)

        result.asserting { res =>
          res.isRight shouldBe true
          res.value shouldBe testApiKeyEntityRead_2.copy(id = res.value.id)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          resApiKeys <- Queries.getAllApiKeys
        } yield resApiKeys).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 2

          val expectedApiKeyRow_1 = (resApiKeys.head._1, testApiKey_1, now, now)
          resApiKeys.head shouldBe expectedApiKeyRow_1

          val expectedApiKeyRow_2 = (resApiKeys(1)._1, testApiKey_2, now, now)
          resApiKeys(1) shouldBe expectedApiKeyRow_2
        }
      }
    }

    "there is the same API Key already in the DB" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          res <- apiKeyDb.insert(testApiKeyEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe ApiKeyAlreadyExistsError
          exc.left.value.message shouldBe "API Key already exists."
        }
      }

      "NOT insert the second API Key into the DB" in {
        val result = for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1).transact(transactor)
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1).transact(transactor).attempt
          resApiKeys <- Queries.getAllApiKeys.transact(transactor)
        } yield resApiKeys

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (resApiKeys.head._1, testApiKey_1, now, now)

          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }
  }

  "ApiKeyDb on get" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = apiKeyDb.getByApiKey(testApiKey_1).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a different API Key in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          res <- apiKeyDb.getByApiKey(testApiKey_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is the same API Key in the DB" should {
      "return Option containing ApiKeyEntity" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          res <- apiKeyDb.getByApiKey(testApiKey_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe testApiKeyEntityRead_1.copy(id = res.get.id)
        }
      }
    }
  }

  "ApiKeyDb on delete" when {

    "there are no API Keys in the DB" should {

      "return false" in {
        val result = apiKeyDb.delete(1L).transact(transactor)

        result.asserting(_ shouldBe false)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDb.delete(1L)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there is an API Key in the DB with different ID" should {

      "return false" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          existingId <- apiKeyDb.getByApiKey(testApiKey_1).map(_.get.id)

          res <- apiKeyDb.delete(existingId + 1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe false)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          existingId <- apiKeyDb.getByApiKey(testApiKey_1).map(_.get.id)

          _ <- apiKeyDb.delete(existingId + 1)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (resApiKeys.head._1, testApiKey_1, now, now)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }

    "there is an API Key in the DB with the given ID" should {

      "return true" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          existingId <- apiKeyDb.getByApiKey(testApiKey_1).map(_.get.id)

          res <- apiKeyDb.delete(existingId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe true)
      }

      "delete this API Key from the DB" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          existingId <- apiKeyDb.getByApiKey(testApiKey_1).map(_.get.id)

          _ <- apiKeyDb.delete(existingId)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[Queries.ApiKeyEntityRaw])
      }
    }

    "there are several API Keys in the DB but only one with the given publicKeyId" should {

      "return true" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          existingId <- apiKeyDb.getByApiKey(testApiKey_1).map(_.get.id)

          res <- apiKeyDb.delete(existingId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe true)
      }

      "delete this API Key from the DB and leave others intact" in {
        val result = (for {
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          _ <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          existingId <- apiKeyDb.getByApiKey(testApiKey_1).map(_.get.id)

          _ <- apiKeyDb.delete(existingId)
          res <- Queries.getAllApiKeys
        } yield res).transact(transactor)

        result.asserting { resApiKeys =>
          resApiKeys.size shouldBe 1

          val expectedApiKeyRow = (resApiKeys.head._1, testApiKey_2, now, now)
          resApiKeys.head shouldBe expectedApiKeyRow
        }
      }
    }
  }
}
