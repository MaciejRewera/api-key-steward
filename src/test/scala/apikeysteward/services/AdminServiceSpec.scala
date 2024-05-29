package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData._
import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.ApiKeyDataNotFound
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError.{
  ApiKeyAlreadyExistsError,
  PublicKeyIdAlreadyExistsError
}
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.utils.Retry.RetryException.MaxNumberOfRetriesExceeded
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

class AdminServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with FixedClock with BeforeAndAfterEach {

  private val apiKeyGenerator = mock[ApiKeyGenerator]
  private val apiKeyRepository = mock[ApiKeyRepository]

  private val adminService = new AdminService(apiKeyGenerator, apiKeyRepository)

  override def beforeEach(): Unit =
    reset(apiKeyGenerator, apiKeyRepository)

  private val createApiKeyAdminRequest = CreateApiKeyRequest(
    name = name,
    description = description,
    ttl = ttlSeconds,
    scopes = List(scopeRead_1, scopeWrite_1)
  )

  private val testException = new RuntimeException("Test Exception")

  "AdminService on createApiKey" when {

    "everything works correctly" should {

      "call ApiKeyGenerator and ApiKeyRepository providing correct ApiKeyData" in {
        apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
        apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.pure(Right(apiKeyData_1))

        for {
          _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest)
          _ = verify(apiKeyGenerator).generateApiKey

          _ = {
            val captor: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
            verify(apiKeyRepository).insert(eqTo(apiKey_1), captor.capture())
            val actualApiKeyData: ApiKeyData = captor.getValue
            actualApiKeyData shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData.publicKeyId)
          }
        } yield ()
      }

      "return the newly created Api Key together with the ApiKeyData returned by ApiKeyRepository" in {
        apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
        apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.pure(Right(apiKeyData_1))

        adminService.createApiKey(userId_1, createApiKeyAdminRequest).asserting(_ shouldBe (apiKey_1, apiKeyData_1))
      }
    }

    "ApiKeyGenerator returns failed IO" should {

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

      "return failed IO containing the same exception" in {
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
            IO.pure(Right(apiKeyData_1))
          )

          for {
            _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest)
            _ = verify(apiKeyGenerator, times(2)).generateApiKey

            _ = {
              val captor_1: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_1), captor_1.capture())
              val actualApiKeyData_1: ApiKeyData = captor_1.getValue
              actualApiKeyData_1 shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData_1.publicKeyId)

              val captor_2: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_2), captor_2.capture())
              val actualApiKeyData_2: ApiKeyData = captor_2.getValue
              actualApiKeyData_2 shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData_2.publicKeyId)
            }
          } yield ()
        }

        "return the second created Api Key together with the ApiKeyData returned by ApiKeyRepository" in {
          apiKeyGenerator.generateApiKey returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
          apiKeyRepository.insert(any[String], any[ApiKeyData]) returns (
            IO.pure(Left(insertionError)),
            IO.pure(Right(apiKeyData_1))
          )

          adminService.createApiKey(userId_1, createApiKeyAdminRequest).asserting(_ shouldBe (apiKey_2, apiKeyData_1))
        }
      }
    }

    Seq(ApiKeyAlreadyExistsError, PublicKeyIdAlreadyExistsError).foreach { insertionError =>
      s"ApiKeyRepository keeps returning ${insertionError.getClass.getSimpleName}" should {

        "call ApiKeyGenerator and ApiKeyRepository again until reaching max retries amount (3)" in {
          apiKeyGenerator.generateApiKey returns (
            IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
          )
          apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.pure(Left(insertionError))

          for {
            _ <- adminService.createApiKey(userId_1, createApiKeyAdminRequest).attempt
            _ = verify(apiKeyGenerator, times(4)).generateApiKey

            _ = {
              val captor_1: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_1), captor_1.capture())
              val actualApiKeyData_1: ApiKeyData = captor_1.getValue
              actualApiKeyData_1 shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData_1.publicKeyId)

              val captor_2: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_2), captor_2.capture())
              val actualApiKeyData_2: ApiKeyData = captor_2.getValue
              actualApiKeyData_2 shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData_2.publicKeyId)

              val captor_3: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_3), captor_3.capture())
              val actualApiKeyData_3: ApiKeyData = captor_3.getValue
              actualApiKeyData_3 shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData_3.publicKeyId)

              val captor_4: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(eqTo(apiKey_4), captor_4.capture())
              val actualApiKeyData_4: ApiKeyData = captor_4.getValue
              actualApiKeyData_4 shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData_4.publicKeyId)
            }
          } yield ()
        }

        "return failed IO containing MaxNumberOfRetriesExceeded" in {
          apiKeyGenerator.generateApiKey returns (
            IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
          )
          apiKeyRepository.insert(any[String], any[ApiKeyData]) returns IO.pure(Left(insertionError))

          adminService.createApiKey(userId_1, createApiKeyAdminRequest).attempt.asserting { result =>
            result shouldBe Left(MaxNumberOfRetriesExceeded(insertionError))
          }
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
      apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

      for {
        _ <- adminService.deleteApiKey(userId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).delete(eqTo(userId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

        adminService.deleteApiKey(userId_1, publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(
          Left(ApiKeyDataNotFound(userId_1, publicKeyId_1))
        )

        adminService
          .deleteApiKey(userId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFound(userId_1, publicKeyId_1)))
      }
    }
  }

  "AdminService on getAllApiKeysFor" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.getAll(any[String]) returns IO.pure(List(apiKeyData_1))

      for {
        _ <- adminService.getAllApiKeysFor(userId_1)
        _ = verify(apiKeyRepository).getAll(eqTo(userId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.getAll(any[String]) returns IO.pure(List(apiKeyData_1, apiKeyData_1, apiKeyData_1))

      adminService.getAllApiKeysFor(userId_1).asserting(_ shouldBe List(apiKeyData_1, apiKeyData_1, apiKeyData_1))
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
