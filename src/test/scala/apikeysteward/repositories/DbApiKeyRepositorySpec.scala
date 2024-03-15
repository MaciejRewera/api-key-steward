package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError._
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity, ClientUsersEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb, ClientUsersDb}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import fs2.Stream
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
  private val clientUsersDb = mock[ClientUsersDb]

  private val apiKeyRepository = new DbApiKeyRepository(apiKeyDb, apiKeyDataDb, clientUsersDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(apiKeyDb, apiKeyDataDb, clientUsersDb)

  private val apiKey = "test-api-key-1"
  private val publicKeyId_1 = UUID.randomUUID()
  private val publicKeyId_2 = UUID.randomUUID()
  private val name = "Test API Key Name"
  private val description = Some("Test key description")
  private val userId_1 = "test-user-id-001"
  private val userId_2 = "test-user-id-002"
  private val ttlSeconds = 60

  private val clientId = "test-client-id-001"

  private val apiKeyData_1 = ApiKeyData(
    publicKeyId = publicKeyId_1,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
  private val apiKeyData_2 = ApiKeyData(
    publicKeyId = publicKeyId_2,
    name = name,
    description = description,
    userId = userId_1,
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
    userId = userId_1,
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
    userId = userId_1,
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
          userId = userId_1,
          expiresAt = now.plusSeconds(ttlSeconds)
        )

        for {
          _ <- apiKeyRepository.insert(apiKey, apiKeyData_1)
          _ <- IO(verify(apiKeyDb).insert(eqTo(expectedEntityWrite)))
          _ <- IO(verify(apiKeyDataDb).insert(eqTo(expectedDataEntityWrite)))
        } yield ()
      }

      "return Right containing ApiKeyData" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped

        apiKeyRepository.insert(apiKey, apiKeyData_1).asserting { result =>
          result.isRight shouldBe true
          result.value shouldBe apiKeyData_1
        }
      }
    }

    "ApiKeyDb returns Left containing ApiKeyAlreadyExistsError" should {

      "NOT call ApiKeyDataDb" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        for {
          _ <- apiKeyRepository.insert(apiKey, apiKeyData_1)
          _ <- IO(verifyZeroInteractions(apiKeyDataDb))
        } yield ()
      }

      "return Left containing ApiKeyAlreadyExistsError" in {
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        apiKeyRepository.insert(apiKey, apiKeyData_1).asserting { result =>
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
            _ <- apiKeyRepository.insert(apiKey, apiKeyData_1)
            _ <- IO(verify(apiKeyDataDb).insert(any[ApiKeyDataEntity.Write]))
            _ <- IO(verifyNoMoreInteractions(apiKeyDataDb))
          } yield ()
        }

        "return Left containing ApiKeyIdAlreadyExistsError" in {
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository.insert(apiKey, apiKeyData_1).asserting { result =>
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
            _ <- apiKeyRepository.insert(apiKey, apiKeyData_1)
            _ <- IO(verify(apiKeyDataDb).insert(any[ApiKeyDataEntity.Write]))
            _ <- IO(verifyNoMoreInteractions(apiKeyDataDb))
          } yield ()
        }

        "return Left containing PublicKeyIdAlreadyExistsError" in {
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository.insert(apiKey, apiKeyData_1).asserting { result =>
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
        "return this entity converted into ApiKeyData" in {
          apiKeyDb.getByApiKey(any[String]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
          apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]

          apiKeyRepository.get(apiKey).asserting { result =>
            result shouldBe defined
            result.get shouldBe apiKeyData_1
          }
        }
      }
    }
  }

  "DbApiKeyRepository on getAll(:userId)" should {

    "call ApiKeyDataDb" in {
      apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAll(userId_1)
        _ <- IO(verify(apiKeyDataDb).getByUserId(eqTo(userId_1)))
      } yield ()
    }

    "NOT call ApiKeyDb" in {
      apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAll(userId_1)
        _ <- IO(verifyZeroInteractions(apiKeyDb))
      } yield ()
    }

    "return values from ApiKeyDataDb" when {

      "ApiKeyDataDb returns empty Stream" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

        apiKeyRepository.getAll(userId_1).asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }

      "ApiKeyDataDb returns elements in Stream" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)

        apiKeyRepository.getAll(userId_1).asserting { result =>
          result.size shouldBe 2
          result shouldBe List(apiKeyData_1, apiKeyData_2)
        }
      }
    }
  }

  "DbApiKeyRepository on delete(:userId, :publicKeyId)" when {

    "should always call ApiKeyDataDb to get ApiKeyDataEntity" in {
      apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)
        _ <- IO(verify(apiKeyDataDb).getBy(eqTo(userId_1), eqTo(publicKeyId_1)))
      } yield ()
    }

    "ApiKeyDataDb.getBy returns empty Option" should {

      "NOT call either ApiKeyDb or ApiKeyDataDb anymore" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

          _ <- IO(verify(apiKeyDataDb, never).copyIntoDeletedTable(any[String], any[UUID]))
          _ <- IO(verify(apiKeyDataDb, never).delete(any[String], any[UUID]))
          _ <- IO(verifyZeroInteractions(apiKeyDb))
        } yield ()
      }

      "return empty Option" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe None)
      }
    }

    "ApiKeyDataDb.getBy returns Option containing ApiKeyDataEntity" when {

      "should always call ApiKeyDataDb.copyIntoDeletedTable" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns false.pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)
          _ <- IO(verify(apiKeyDataDb).copyIntoDeletedTable(eqTo(userId_1), eqTo(publicKeyId_1)))
        } yield ()
      }

      "ApiKeyDataDb.copyIntoDeletedTable returns failure (no rows copied)" should {

        "NOT call either ApiKeyDb or ApiKeyDataDb delete methods" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns false.pure[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

            _ <- IO(verify(apiKeyDataDb, never).delete(any[String], any[UUID]))
            _ <- IO(verifyZeroInteractions(apiKeyDb))
          } yield ()
        }

        "return empty Option" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns false.pure[doobie.ConnectionIO]

          apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe None)
        }
      }

      "ApiKeyDataDb.copyIntoDeletedTable returns success (row copied)" when {

        "always call ApiKeyDataDb.delete" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
          apiKeyDataDb.delete(any[String], any[UUID]) returns false.pure[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)
            _ <- IO(verify(apiKeyDataDb).delete(eqTo(userId_1), eqTo(publicKeyId_1)))
          } yield ()
        }

        "ApiKeyDataDb.delete returns failure" should {

          "NOT call ApiKeyDb.delete" in {
            apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
            apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
            apiKeyDataDb.delete(any[String], any[UUID]) returns false.pure[doobie.ConnectionIO]

            for {
              _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)
              _ <- IO(verifyZeroInteractions(apiKeyDb))
            } yield ()
          }

          "return empty Option" in {
            apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
            apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
            apiKeyDataDb.delete(any[String], any[UUID]) returns false.pure[doobie.ConnectionIO]

            apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe None)
          }
        }

        "ApiKeyDataDb.delete returns success" when {

          "should always call ApiKeyDb.delete" in {
            apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
            apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
            apiKeyDataDb.delete(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
            apiKeyDb.delete(any[Long]) returns false.pure[doobie.ConnectionIO]

            for {
              _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)
              _ <- IO(verify(apiKeyDb).delete(eqTo(apiKeyDataEntityRead_1.apiKeyId)))
            } yield ()
          }

          "ApiKeyDb.delete returns failure" should {
            "return empty Option" in {
              apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1)
                .pure[doobie.ConnectionIO]
              apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
              apiKeyDataDb.delete(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
              apiKeyDb.delete(any[Long]) returns false.pure[doobie.ConnectionIO]

              apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe None)
            }
          }

          "ApiKeyDb.delete returns success" should {
            "return Option containing deleted ApiKeyData" in {
              apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1)
                .pure[doobie.ConnectionIO]
              apiKeyDataDb.copyIntoDeletedTable(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
              apiKeyDataDb.delete(any[String], any[UUID]) returns true.pure[doobie.ConnectionIO]
              apiKeyDb.delete(any[Long]) returns true.pure[doobie.ConnectionIO]

              apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
            }
          }
        }
      }
    }
  }

  "DbApiKeyRepository on getAllUserIds" should {

    "call ClientUsersDb" in {
      clientUsersDb.getAllByClientId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAllUserIds(clientId)
        _ <- IO(verify(clientUsersDb).getAllByClientId(eqTo(clientId)))
      } yield ()
    }

    "return userIds obtained from ClientUsersDb" when {

      "ClientUsersDb returns empty Stream" in {
        clientUsersDb.getAllByClientId(any[String]) returns Stream.empty

        apiKeyRepository.getAllUserIds(clientId).asserting(_ shouldBe List.empty[String])
      }

      "ClientUsersDb returns elements in Stream" in {
        val clientUsersEntityRead_1 = ClientUsersEntity.Read(1L, clientId, userId_1, now, now)
        val clientUsersEntityRead_2 = ClientUsersEntity.Read(2L, clientId, userId_2, now, now)
        clientUsersDb.getAllByClientId(any[String]) returns Stream(clientUsersEntityRead_1, clientUsersEntityRead_2)

        apiKeyRepository.getAllUserIds(clientId).asserting { result =>
          result.size shouldBe 2
          result shouldBe List(userId_1, userId_2)
        }
      }
    }
  }
}
