package apikeysteward.repositories.db

import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyIdAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID

class ApiKeyDataDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE api_key, api_key_data".update.run
  } yield ()

  private val now = Instant.parse("2024-02-15T12:34:56Z")
  implicit private def fixedClock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  private val apiKeyDb = new ApiKeyDb
  private val apiKeyDataDb = new ApiKeyDataDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApiKeysData =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList
  }

  private val ttlSeconds = 60
  private val publicKeyId_1 = UUID.randomUUID().toString
  private val publicKeyId_2 = UUID.randomUUID().toString
  private val publicKeyId_3 = UUID.randomUUID().toString
  private val testUserId_1 = "test-user-001"
  private val testUserId_2 = "test-user-002"
  private val testUserId_3 = "test-user-003"

  private val testApiKey_1 = "test-api-key-1"
  private val testApiKey_2 = "test-api-key-2"
  private val testApiKey_3 = "test-api-key-3"
  private val testApiKeyEntityWrite_1 = ApiKeyEntity.Write(testApiKey_1)
  private val testApiKeyEntityWrite_2 = ApiKeyEntity.Write(testApiKey_2)
  private val testApiKeyEntityWrite_3 = ApiKeyEntity.Write(testApiKey_3)

  private val apiKeyDataEntityWrite_1 = ApiKeyDataEntity.Write(
    apiKeyId = 1L,
    publicKeyId = publicKeyId_1,
    name = "Test API Key name no. 1",
    description = Some("Test key description no. 1"),
    userId = testUserId_1,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
  private val apiKeyDataEntityRead_1 = ApiKeyDataEntity.Read(
    id = 1L,
    apiKeyId = 1L,
    publicKeyId = publicKeyId_1,
    name = "Test API Key name no. 1",
    description = Some("Test key description no. 1"),
    userId = testUserId_1,
    expiresAt = now.plusSeconds(ttlSeconds),
    createdAt = now,
    updatedAt = now
  )

  private val apiKeyDataEntityWrite_2 = ApiKeyDataEntity.Write(
    apiKeyId = 2L,
    publicKeyId = publicKeyId_2,
    name = "Test API Key name no. 2",
    description = Some("Test key description no. 2"),
    userId = testUserId_2,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
  private val apiKeyDataEntityRead_2 = ApiKeyDataEntity.Read(
    id = 2L,
    apiKeyId = 2L,
    publicKeyId = publicKeyId_2,
    name = "Test API Key name no. 2",
    description = Some("Test key description no. 2"),
    userId = testUserId_2,
    expiresAt = now.plusSeconds(ttlSeconds),
    createdAt = now,
    updatedAt = now
  )

  private val apiKeyDataEntityWrite_3 = ApiKeyDataEntity.Write(
    apiKeyId = 3L,
    publicKeyId = publicKeyId_3,
    name = "Test API Key name no. 3",
    description = Some("Test key description no. 3"),
    userId = testUserId_3,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
  private val apiKeyDataEntityRead_3 = ApiKeyDataEntity.Read(
    id = 3L,
    apiKeyId = 3L,
    publicKeyId = publicKeyId_3,
    name = "Test API Key name no. 3",
    description = Some("Test key description no. 3"),
    userId = testUserId_3,
    expiresAt = now.plusSeconds(ttlSeconds),
    createdAt = now,
    updatedAt = now
  )

  "ApiKeyDataDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
        } yield res).transact(transactor)

        result.asserting { res =>
          res.isRight shouldBe true
          res.value shouldBe apiKeyDataEntityRead_1.copy(id = res.value.id, apiKeyId = 1L)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
          resApiKeysData <- Queries.getAllApiKeysData
        } yield resApiKeysData).transact(transactor)

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 1

          val resApiKeyData = resApiKeysData.head
          resApiKeyData shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData.id,
            apiKeyId = resApiKeyData.apiKeyId
          )
        }
      }
    }

    "there is a row in the DB with the same data, but different apiKeyId and publicKeyId" should {

      "return inserted entity" in {
        val result = (for {
          apiKeyEntityRead_1 <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId_1 = apiKeyEntityRead_1.value.id
          apiKeyEntityRead_2 <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          apiKeyId_2 = apiKeyEntityRead_2.value.id

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))
          inserted <- apiKeyDataDb.insert(
            apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2, publicKeyId = publicKeyId_2)
          )
        } yield inserted).transact(transactor)

        result.asserting { res =>
          res.isRight shouldBe true
          res.value shouldBe apiKeyDataEntityRead_1.copy(
            id = res.value.id,
            apiKeyId = res.value.apiKeyId,
            publicKeyId = publicKeyId_2
          )
        }
      }

      "insert entity into DB" in {
        val result = (for {
          apiKeyEntityRead_1 <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId_1 = apiKeyEntityRead_1.value.id
          apiKeyEntityRead_2 <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          apiKeyId_2 = apiKeyEntityRead_2.value.id

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2, publicKeyId = publicKeyId_2))
          resApiKeysData <- Queries.getAllApiKeysData
        } yield resApiKeysData).transact(transactor)

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 2

          val resApiKeyData_1 = resApiKeysData.head
          resApiKeysData.head shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData_1.id,
            apiKeyId = resApiKeyData_1.apiKeyId
          )

          val resApiKeyData_2 = resApiKeysData(1)
          resApiKeysData(1) shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData_2.id,
            apiKeyId = resApiKeyData_2.apiKeyId,
            publicKeyId = publicKeyId_2
          )
        }
      }
    }

    "there is a row in the DB with the same apiKeyId" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId, publicKeyId = publicKeyId_2))
        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe ApiKeyIdAlreadyExistsError
          exc.left.value.message shouldBe "API Key Data with the same apiKeyId already exists."
        }
      }

      "NOT insert the second entity into the DB" in {
        val result = for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1).transact(transactor)
          apiKeyId = apiKeyEntityRead.value.id

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId)).transact(transactor)
          _ <- apiKeyDataDb
            .insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId, publicKeyId = publicKeyId_2))
            .transact(transactor)
            .attempt

          resApiKeysData <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeysData

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 1

          val resApiKeyData = resApiKeysData.head
          resApiKeysData.head shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData.id,
            apiKeyId = resApiKeyData.apiKeyId
          )
        }
      }
    }

    "there is a row in the DB with the same publicKeyId" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          apiKeyEntityRead_1 <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId_1 = apiKeyEntityRead_1.value.id
          apiKeyEntityRead_2 <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          apiKeyId_2 = apiKeyEntityRead_2.value.id

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2))

        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe PublicKeyIdAlreadyExistsError
          exc.left.value.message shouldBe "API Key Data with the same publicKeyId already exists."
        }
      }

      "NOT insert the second entity into the DB" in {
        val result = for {
          apiKeyEntityRead_1 <- apiKeyDb.insert(testApiKeyEntityWrite_1).transact(transactor)
          apiKeyId_1 = apiKeyEntityRead_1.value.id
          apiKeyEntityRead_2 <- apiKeyDb.insert(testApiKeyEntityWrite_2).transact(transactor)
          apiKeyId_2 = apiKeyEntityRead_2.value.id

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1)).transact(transactor)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2)).transact(transactor).attempt

          resApiKeysData <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeysData

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 1

          val resApiKeyData = resApiKeysData.head
          resApiKeysData.head shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData.id,
            apiKeyId = resApiKeyData.apiKeyId
          )
        }
      }
    }
  }

  "ApiKeyDataDb on getByApiKeyId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = apiKeyDataDb.getByApiKeyId(123L).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different apiKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByApiKeyId(apiKeyId + 1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same apiKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByApiKeyId(apiKeyId)
        } yield (res, apiKeyId)).transact(transactor)

        result.asserting { case (res, apiKeyId) =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = apiKeyId)
        }
      }
    }
  }

  "ApiKeyDataDb on getByUserId" when {

    "there are no rows in the DB" should {
      "return empty List" in {
        val result = apiKeyDataDb.getByUserId(testUserId_1).compile.toList.transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with different userId" should {
      "return empty List" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByUserId(testUserId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same userId" should {
      "return List containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByUserId(testUserId_1).compile.toList
        } yield (res, apiKeyId)).transact(transactor)

        result.asserting { case (res, apiKeyId) =>
          res.size shouldBe 1
          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = apiKeyId)
        }
      }
    }

    "there is are several rows in the DB with the same userId together with rows with different userIds" should {
      "return List containing all matching ApiKeyDataEntities" in {
        val result = (for {
          apiKeyEntityRead_1 <- apiKeyDb.insert(testApiKeyEntityWrite_1)
          apiKeyId_1 = apiKeyEntityRead_1.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyEntityRead_2 <- apiKeyDb.insert(testApiKeyEntityWrite_2)
          apiKeyId_2 = apiKeyEntityRead_2.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2, userId = testUserId_1))

          apiKeyEntityRead_3 <- apiKeyDb.insert(testApiKeyEntityWrite_3)
          apiKeyId_3 = apiKeyEntityRead_3.value.id
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3))

          res <- apiKeyDataDb.getByUserId(testUserId_1).compile.toList
        } yield (res, apiKeyId_1, apiKeyId_2)).transact(transactor)

        result.asserting { case (res, apiKeyId_1, apiKeyId_2) =>
          res.size shouldBe 2
          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = apiKeyId_1)
          res(1) shouldBe apiKeyDataEntityRead_2.copy(id = res(1).id, apiKeyId = apiKeyId_2, userId = testUserId_1)
        }
      }
    }
  }
}
