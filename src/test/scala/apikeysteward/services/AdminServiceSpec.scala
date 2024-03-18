package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.repositories.{ApiKeyRepository, DoobieUnitSpec}
import apikeysteward.routes.model.admin.CreateApiKeyAdminRequest
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyNoMoreInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class AdminServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach {

  private val apiKeyGenerator = mock[ApiKeyGenerator[String]]
  private val apiKeyRepository = mock[ApiKeyRepository[String]]

  private val adminService = new AdminService(apiKeyGenerator, apiKeyRepository)

  override def beforeEach(): Unit =
    reset(apiKeyGenerator, apiKeyRepository)

  private val apiKey_1 = "test-api-key-1"
  private val apiKey_2 = "test-api-key-2"
  private val publicKeyId_1 = UUID.randomUUID()
  private val name = "Test API Key Name"
  private val description = Some("Test key description")
  private val userId_1 = "test-user-id-001"
  private val userId_2 = "test-user-id-002"
  private val ttlSeconds = 60

  private val apiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_1,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds)
  )

  private val createApiKeyAdminRequest = CreateApiKeyAdminRequest(
    name = name,
    description = description,
    ttl = ttlSeconds
  )

  private val testException = new RuntimeException("Test Exception")

  "AdminService on createApiKey" when {

    "everything works correctly" should {

      "call ApiKeyGenerator and ApiKeyRepository providing correct ApiKeyData" in {
        apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
        apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.pure(Right(apiKeyData))

        for {
          _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest)
          _ = verify(apiKeyGenerator).generateApiKey

          _ = {
            val captor: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
            verify(apiKeyRepository).insert(eqTo(apiKey_1), captor.capture())
            val actualApiKeyData: ApiKeyData = captor.getValue
            actualApiKeyData shouldBe apiKeyData.copy(publicKeyId = actualApiKeyData.publicKeyId)
          }
        } yield ()
      }

      "return the newly created Api Key together with the ApiKeyData returned by ApiKeyRepository" in {
        apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
        apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.pure(Right(apiKeyData))

        adminService.createApiKey(userId_1, createApiKeyAdminRequest).asserting(_ shouldBe (apiKey_1, apiKeyData))
      }
    }

    "ApiKeyGenerator returns exception" should {

      "NOT call ApiKeyGenerator again" in {
        apiKeyGenerator.generateApiKey returns IO.raiseError(testException)

        for {
          _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest).attempt
          _ = verify(apiKeyGenerator).generateApiKey
          _ = verifyNoMoreInteractions(apiKeyGenerator)
        } yield ()
      }

      "NOT call ApiKeyRepository" in {
        apiKeyGenerator.generateApiKey returns IO.raiseError(testException)

        for {
          _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest).attempt
          _ = verifyNoInteractions(apiKeyRepository)
        } yield ()
      }

      "return IO with this exception" in {
        apiKeyGenerator.generateApiKey returns IO.raiseError(testException)

        adminService.createApiKey(userId_1, createApiKeyAdminRequest).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    Seq(ApiKeyAlreadyExistsError, PublicKeyIdAlreadyExistsError).foreach { insertionError =>
      s"ApiKeyRepository returns ${insertionError.getClass.getSimpleName} on the first try" should {

        "call ApiKeyGenerator and ApiKeyRepository again" in {
          apiKeyGenerator.generateApiKey returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
          apiKeyRepository.insert(any[String], any[ApiKeyData]) returns (
            IO.pure(Left(insertionError)),
            IO.pure(Right(apiKeyData))
          )

          for {
            _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest)
            _ = verify(apiKeyGenerator, times(2)).generateApiKey

            _ = {
              val captor_1: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_1), captor_1.capture())
              val actualApiKeyData_1: ApiKeyData = captor_1.getValue
              actualApiKeyData_1 shouldBe apiKeyData.copy(publicKeyId = actualApiKeyData_1.publicKeyId)

              val captor_2: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_2), captor_2.capture())
              val actualApiKeyData_2: ApiKeyData = captor_2.getValue
              actualApiKeyData_2 shouldBe apiKeyData.copy(publicKeyId = actualApiKeyData_2.publicKeyId)
            }
          } yield ()
        }

        "return the second created Api Key together with the ApiKeyData returned by ApiKeyRepository" in {
          apiKeyGenerator.generateApiKey returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
          apiKeyRepository.insert(any[String], any[ApiKeyData]) returns (
            IO.pure(Left(insertionError)),
            IO.pure(Right(apiKeyData))
          )

          adminService.createApiKey(userId_1, createApiKeyAdminRequest).asserting(_ shouldBe (apiKey_2, apiKeyData))
        }
      }
    }

    "ApiKeyRepository returns a different exception" should {
      "return IO with this exception" in {
        apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
        apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.raiseError(testException)

        adminService.createApiKey(userId_1, createApiKeyAdminRequest).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "AdminService on deleteApiKey" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(Some(apiKeyData))

      for {
        _ <- adminService.deleteApiKey(userId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).delete(eqTo(userId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(Some(apiKeyData))

      adminService.deleteApiKey(userId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData))
    }
  }

  "AdminService on getAllApiKeysFor" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.getAll(any[String]) returns IO.pure(List(apiKeyData))

      for {
        _ <- adminService.getAllApiKeysFor(userId_1)
        _ = verify(apiKeyRepository).getAll(eqTo(userId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.getAll(any[String]) returns IO.pure(List(apiKeyData, apiKeyData, apiKeyData))

      adminService.getAllApiKeysFor(userId_1).asserting(_ shouldBe List(apiKeyData, apiKeyData, apiKeyData))
    }
  }

  "AdminService on getAllUserIds" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.getAllUserIds returns IO.pure(List(userId_1, userId_2))

      for {
        _ <- adminService.getAllUserIds
        _ = verify(apiKeyRepository).getAllUserIds
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.getAllUserIds returns IO.pure(List(userId_1, userId_2))

      adminService.getAllUserIds.asserting(_ shouldBe List(userId_1, userId_2))
    }
  }
}
