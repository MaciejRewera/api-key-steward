package apikeysteward.repositories

import fs2.Stream
import apikeysteward.base.FixedClock
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyAlreadyExistsError,
  ApiKeyIdAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito.verify
import org.mockito.MockitoSugar._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.util.UUID

class DbApiKeyRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val apiKeyDb = mock[ApiKeyDb]
  private val apiKeyDataDb = mock[ApiKeyDataDb]

  private val apiKeyRepository = new DbApiKeyRepository(apiKeyDb, apiKeyDataDb, noopTransactor)

  override def beforeEach(): Unit =
    reset(apiKeyDb, apiKeyDataDb)

  private val apiKey = "test-api-key-1"
  private val publicKeyId_1 = UUID.randomUUID()
  private val publicKeyId_2 = UUID.randomUUID()
  private val name = "Test API Key Name"
  private val description = Some("Test key description")
  private val userId = "test-user-001"
  private val ttlSeconds = 60

  private val apiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_1,
    name = name,
    description = description,
    userId = userId,
    expiresAt = now.plusSeconds(ttlSeconds)
  )

  private val apiKeyEntityRead = ApiKeyEntity.Read(
    id = 2L,
    createdAt = now,
    updatedAt = now
  )

  private val apiKeyDataEntityRead_1 = ApiKeyDataEntity.Read(
    id = 1L,
    apiKeyId = 2L,
    publicKeyId = publicKeyId_1.toString,
    name = name,
    description = description,
    userId = userId,
    expiresAt = now.plusSeconds(ttlSeconds),
    createdAt = now,
    updatedAt = now
  )
  private val apiKeyDataEntityRead_2 = ApiKeyDataEntity.Read(
    id = 2L,
    apiKeyId = 3L,
    publicKeyId = publicKeyId_2.toString,
    name = name,
    description = description,
    userId = userId,
    expiresAt = now.plusSeconds(ttlSeconds),
    createdAt = now,
    updatedAt = now
  )

  private val apiKeyAlreadyExistsErrorWrapped =
    ApiKeyAlreadyExistsError.asInstanceOf[ApiKeyInsertionError].asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

  private val apiKeyIdAlreadyExistsErrorWrapped =
    ApiKeyIdAlreadyExistsError
      .asInstanceOf[ApiKeyInsertionError]
      .asLeft[ApiKeyDataEntity.Read]
      .pure[doobie.ConnectionIO]

  private val publicKeyIdAlreadyExistsErrorWrapped =
    PublicKeyIdAlreadyExistsError
      .asInstanceOf[ApiKeyInsertionError]
      .asLeft[ApiKeyDataEntity.Read]
      .pure[doobie.ConnectionIO]

  private val apiKeyEntityReadWrapped = apiKeyEntityRead.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]
  private val apiKeyDataEntityReadWrapped =
    apiKeyDataEntityRead_1.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]

  "DbApiKeyRepository on insert" when {

    "everything works correctly" should {

      "call ApiKeyDb and ApiKeyDataDb once, providing correct entities" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped

        val expectedEntityWrite = ApiKeyEntity.Write(apiKey)
        val expectedDataEntityWrite = ApiKeyDataEntity.Write(
          apiKeyId = 2L,
          publicKeyId = publicKeyId_1.toString,
          name = name,
          description = description,
          userId = userId,
          expiresAt = now.plusSeconds(ttlSeconds)
        )

        for {
          _ <- apiKeyRepository.insert(apiKey, apiKeyData)
          _ <- IO(verify(apiKeyDb).insert(eqTo(expectedEntityWrite)))
          _ <- IO(verify(apiKeyDataDb).insert(eqTo(expectedDataEntityWrite)))
        } yield ()
      }

      "return Right containing ApiKeyData" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped

        apiKeyRepository.insert(apiKey, apiKeyData).asserting { result =>
          result.isRight shouldBe true
          result.value shouldBe apiKeyData
        }
      }
    }

    "ApiKeyDb returns Left containing ApiKeyAlreadyExistsError" should {

      "NOT call ApiKeyDataDb" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        for {
          _ <- apiKeyRepository.insert(apiKey, apiKeyData)
          _ <- IO(verifyZeroInteractions(apiKeyDataDb))
        } yield ()
      }

      "return Left containing ApiKeyAlreadyExistsError" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        apiKeyRepository.insert(apiKey, apiKeyData).asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe ApiKeyAlreadyExistsError
        }
      }
    }

    "ApiKeyDb returns Right containing ApiKeyEntity.Read" when {

      "ApiKeyDataDb returns Left containing ApiKeyIdAlreadyExistsError" should {

        "NOT call ApiKeyDataDb again" in {
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          for {
            _ <- apiKeyRepository.insert(apiKey, apiKeyData)
            _ <- IO(verify(apiKeyDataDb).insert(any[ApiKeyDataEntity.Write]))
            _ <- IO(verifyNoMoreInteractions(apiKeyDataDb))
          } yield ()
        }

        "return Left containing ApiKeyIdAlreadyExistsError" in {
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository.insert(apiKey, apiKeyData).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ApiKeyIdAlreadyExistsError
          }
        }
      }

      "ApiKeyDataDb returns Left containing PublicKeyIdAlreadyExistsError" should {

        "NOT call ApiKeyDataDb again" in {
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          for {
            _ <- apiKeyRepository.insert(apiKey, apiKeyData)
            _ <- IO(verify(apiKeyDataDb).insert(any[ApiKeyDataEntity.Write]))
            _ <- IO(verifyNoMoreInteractions(apiKeyDataDb))
          } yield ()
        }

        "return Left containing PublicKeyIdAlreadyExistsError" in {
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository.insert(apiKey, apiKeyData).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe PublicKeyIdAlreadyExistsError
          }
        }
      }
    }
  }

  "DbApiKeyRepository on get(:apiKey)" when {

    "should always call ApiKeyDb" in {
      apiKeyDb.getByApiKey(any[String]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.get(apiKey)
        _ <- IO(verify(apiKeyDb).getByApiKey(eqTo(apiKey)))
      } yield ()
    }

    "ApiKeyDb returns empty Option" should {

      "NOT call ApiKeyDataDb" in {
        apiKeyDb.getByApiKey(any[String]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.get(apiKey)
          _ <- IO(verifyZeroInteractions(apiKeyDataDb))
        } yield ()
      }

      "return empty Option" in {
        apiKeyDb.getByApiKey(any[String]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository.get(apiKey).asserting(_ shouldBe None)
      }
    }

    "ApiKeyDb returns ApiKeyEntity" when {

      "should always call ApiKeyDataDb" in {
        apiKeyDb.getByApiKey(any[String]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
        apiKeyDataDb.getByApiKeyId(any[Long]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.get(apiKey)
          _ <- IO(verify(apiKeyDataDb).getByApiKeyId(eqTo(apiKeyEntityRead.id)))
        } yield ()
      }

      "ApiKeyDataDb returns empty Option" should {
        "return empty Option" in {
          apiKeyDb.getByApiKey(any[String]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
          apiKeyDataDb.getByApiKeyId(any[Long]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

          apiKeyRepository.get(apiKey).asserting(_ shouldBe None)
        }
      }

      "ApiKeyDataDb returns ApiKeyDataEntity" should {
        "return this ApiKeyDataEntity" in {
          apiKeyDb.getByApiKey(any[String]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
          apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]

          apiKeyRepository.get(apiKey).asserting { result =>
            result shouldBe defined
            result.get shouldBe apiKeyDataEntityRead_1
          }
        }
      }
    }
  }

  "DbApiKeyRepository on getAll(:userId)" should {

    "call ApiKeyDataDb" in {
      apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAll(userId)
        _ <- IO(verify(apiKeyDataDb).getByUserId(eqTo(userId)))
      } yield ()
    }

    "NOT call ApiKeyDb" in {
      apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAll(userId)
        _ <- IO(verifyZeroInteractions(apiKeyDb))
      } yield ()
    }

    "return values from ApiKeyDataDb" when {

      "ApiKeyDataDb returns empty Stream" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

        apiKeyRepository.getAll(userId).asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }

      "ApiKeyDataDb returns elements in Stream" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)

        apiKeyRepository.getAll(userId).asserting { result =>
          result.size shouldBe 2
          result.head shouldBe apiKeyDataEntityRead_1
          result(1) shouldBe apiKeyDataEntityRead_2
        }
      }
    }
  }
}
