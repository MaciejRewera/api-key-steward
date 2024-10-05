package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData._
import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate}
import apikeysteward.repositories.ApiKeyRepository
import apikeysteward.routes.model.admin.apikey.UpdateApiKeyAdminRequest
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.NotAllowedScopesProvidedError
import cats.data.NonEmptyChain
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

class ApiKeyManagementServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val createApiKeyRequestValidator = mock[CreateApiKeyRequestValidator]
  private val apiKeyGenerator = mock[ApiKeyGenerator]
  private val apiKeyRepository = mock[ApiKeyRepository]

  private val managementService =
    new ApiKeyManagementService(createApiKeyRequestValidator, apiKeyGenerator, uuidGenerator, apiKeyRepository)

  private val createApiKeyRequest = CreateApiKeyRequest(
    name = name,
    description = description,
    ttl = ttlMinutes,
    scopes = List(scopeRead_1, scopeWrite_1)
  )

  override def beforeEach(): Unit = {
    reset(createApiKeyRequestValidator, apiKeyGenerator, uuidGenerator, apiKeyRepository)

    createApiKeyRequestValidator.validateCreateRequest(any[CreateApiKeyRequest]) returns Right(createApiKeyRequest)
    apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
    uuidGenerator.generateUuid returns IO.pure(publicKeyId_1)
    apiKeyRepository.insert(any[ApiKey], any[ApiKeyData]) returns IO.pure(Right(apiKeyData_1))
  }

  private val testException = new RuntimeException("Test Exception")

  "ManagementService on createApiKey" when {

    "everything works correctly" should {

      "call CreateApiKeyRequestValidator, ApiKeyGenerator, UuidGenerator and ApiKeyRepository providing correct ApiKeyData" in {
        for {
          _ <- managementService.createApiKey(userId_1, createApiKeyRequest)

          _ = verify(createApiKeyRequestValidator).validateCreateRequest(eqTo(createApiKeyRequest))
          _ = verify(apiKeyGenerator).generateApiKey
          _ = verify(uuidGenerator).generateUuid
          _ = {
            val captor: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
            verify(apiKeyRepository).insert(eqTo(apiKey_1), captor.capture())
            val actualApiKeyData: ApiKeyData = captor.getValue
            actualApiKeyData shouldBe apiKeyData_1.copy(publicKeyId = actualApiKeyData.publicKeyId)
          }
        } yield ()
      }

      "return the newly created Api Key together with the ApiKeyData returned by ApiKeyRepository" in {
        managementService
          .createApiKey(userId_1, createApiKeyRequest)
          .asserting(_ shouldBe Right(apiKey_1 -> apiKeyData_1))
      }
    }

    "CreateApiKeyRequestValidator returns Left" should {

      "NOT call ApiKeyGenerator, UuidGenerator or ApiKeyRepository" in {
        createApiKeyRequestValidator.validateCreateRequest(any[CreateApiKeyRequest]) returns Left(
          NonEmptyChain.one(NotAllowedScopesProvidedError(Set(scopeRead_2, scopeWrite_2)))
        )

        for {
          _ <- managementService.createApiKey(userId_1, createApiKeyRequest)

          _ = verifyNoInteractions(apiKeyGenerator, uuidGenerator, apiKeyRepository)
        } yield ()
      }

      "return successful IO containing Left with ValidationError" in {
        val error = NotAllowedScopesProvidedError(Set(scopeRead_2, scopeWrite_2))
        createApiKeyRequestValidator.validateCreateRequest(any[CreateApiKeyRequest]) returns Left(
          NonEmptyChain.one(error)
        )

        managementService
          .createApiKey(userId_1, createApiKeyRequest)
          .asserting(_ shouldBe Left(ValidationError(Seq(error))))
      }
    }

    "ApiKeyGenerator returns failed IO" should {

      "NOT call ApiKeyGenerator again" in {
        apiKeyGenerator.generateApiKey returns IO.raiseError(testException)

        for {
          _ <- managementService.createApiKey(userId_1, createApiKeyRequest).attempt
          _ = verify(apiKeyGenerator).generateApiKey
          _ = verifyNoMoreInteractions(apiKeyGenerator)
        } yield ()
      }

      "NOT call UuidGenerator or ApiKeyRepository" in {
        apiKeyGenerator.generateApiKey returns IO.raiseError(testException)

        for {
          _ <- managementService.createApiKey(userId_1, createApiKeyRequest).attempt
          _ = verifyNoInteractions(uuidGenerator, apiKeyRepository)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyGenerator.generateApiKey returns IO.raiseError(testException)

        managementService
          .createApiKey(userId_1, createApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call UuidGenerator again" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- managementService.createApiKey(userId_1, createApiKeyRequest).attempt
          _ = verify(uuidGenerator).generateUuid
          _ = verifyNoMoreInteractions(uuidGenerator)
        } yield ()
      }

      "NOT call ApiKeyRepository" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- managementService.createApiKey(userId_1, createApiKeyRequest).attempt
          _ = verifyNoInteractions(apiKeyRepository)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        managementService
          .createApiKey(userId_1, createApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    Seq(ApiKeyAlreadyExistsError, PublicKeyIdAlreadyExistsError).foreach { insertionError =>
      s"ApiKeyRepository returns ${insertionError.getClass.getSimpleName} on the first try" should {

        "call ApiKeyGenerator, UuidGenerator and ApiKeyRepository again" in {
          apiKeyGenerator.generateApiKey returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
          uuidGenerator.generateUuid returns (IO.pure(publicKeyId_1), IO.pure(publicKeyId_2))
          apiKeyRepository.insert(any[ApiKey], any[ApiKeyData]) returns (
            IO.pure(Left(insertionError)),
            IO.pure(Right(apiKeyData_1))
          )

          for {
            _ <- managementService.createApiKey(userId_1, createApiKeyRequest)
            _ = verify(apiKeyGenerator, times(2)).generateApiKey
            _ = verify(uuidGenerator, times(2)).generateUuid

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
          uuidGenerator.generateUuid returns (IO.pure(publicKeyId_1), IO.pure(publicKeyId_2))
          apiKeyRepository.insert(any[ApiKey], any[ApiKeyData]) returns (
            IO.pure(Left(insertionError)),
            IO.pure(Right(apiKeyData_1))
          )

          managementService
            .createApiKey(userId_1, createApiKeyRequest)
            .asserting(_ shouldBe Right(apiKey_2 -> apiKeyData_1))
        }
      }
    }

    Seq(ApiKeyAlreadyExistsError, PublicKeyIdAlreadyExistsError).foreach { dbInsertionError =>
      s"ApiKeyRepository keeps returning ${dbInsertionError.getClass.getSimpleName}" should {

        "call ApiKeyGenerator and ApiKeyRepository again until reaching max retries amount (3)" in {
          apiKeyGenerator.generateApiKey returns (
            IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
          )
          uuidGenerator.generateUuid returns (
            IO.pure(publicKeyId_1), IO.pure(publicKeyId_2), IO.pure(publicKeyId_3), IO.pure(publicKeyId_4)
          )
          apiKeyRepository.insert(any[ApiKey], any[ApiKeyData]) returns IO.pure(Left(dbInsertionError))

          for {
            _ <- managementService.createApiKey(userId_1, createApiKeyRequest).attempt
            _ = verify(apiKeyGenerator, times(4)).generateApiKey
            _ = verify(uuidGenerator, times(4)).generateUuid

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

        "return successful IO containing Left with InsertionError" in {
          apiKeyGenerator.generateApiKey returns (
            IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
          )
          uuidGenerator.generateUuid returns (
            IO.pure(publicKeyId_1), IO.pure(publicKeyId_2), IO.pure(publicKeyId_3), IO.pure(publicKeyId_4)
          )
          apiKeyRepository.insert(any[ApiKey], any[ApiKeyData]) returns IO.pure(Left(dbInsertionError))

          managementService.createApiKey(userId_1, createApiKeyRequest).asserting { result =>
            result shouldBe Left(InsertionError(dbInsertionError))
          }
        }
      }
    }

    "ApiKeyRepository returns a different exception" should {
      "return IO with this exception" in {
        apiKeyGenerator.generateApiKey returns IO.pure(apiKey_1)
        apiKeyRepository.insert(any[ApiKey], any[ApiKeyData]) returns IO.raiseError(testException)

        managementService
          .createApiKey(userId_1, createApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on updateApiKey" should {

    val updateApiKeyRequest = UpdateApiKeyAdminRequest(name = nameUpdated, description = descriptionUpdated)

    val outputApiKeyData = ApiKeyData(
      publicKeyId = publicKeyId_1,
      name = nameUpdated,
      description = descriptionUpdated,
      userId = userId_1,
      expiresAt = ApiKeyExpirationCalculator.calcExpiresAt(42),
      scopes = apiKeyData_1.scopes
    )

    "call ApiKeyRepository" in {
      apiKeyRepository.update(any[ApiKeyDataUpdate]) returns IO.pure(Right(outputApiKeyData))

      for {
        _ <- managementService.updateApiKey(publicKeyId_1, updateApiKeyRequest)
        _ = verify(apiKeyRepository).update(eqTo(apiKeyDataUpdate_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.update(any[ApiKeyDataUpdate]) returns IO.pure(Right(outputApiKeyData))

        managementService
          .updateApiKey(publicKeyId_1, updateApiKeyRequest)
          .asserting(
            _ shouldBe Right(outputApiKeyData)
          )
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository.update(any[ApiKeyDataUpdate]) returns IO.pure(
          Left(ApiKeyDbError.ApiKeyDataNotFoundError(userId_1, publicKeyIdStr_1))
        )

        managementService
          .updateApiKey(publicKeyId_1, updateApiKeyRequest)
          .asserting(
            _ shouldBe Left(ApiKeyDbError.ApiKeyDataNotFoundError(userId_1, publicKeyIdStr_1))
          )
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.update(any[ApiKeyDataUpdate]) returns IO.raiseError(testException)

        managementService
          .updateApiKey(publicKeyId_1, updateApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on deleteApiKeyBelongingToUserWith" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

      for {
        _ <- managementService.deleteApiKeyBelongingToUserWith(userId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).delete(eqTo(userId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

        managementService
          .deleteApiKeyBelongingToUserWith(userId_1, publicKeyId_1)
          .asserting(_ shouldBe Right(apiKeyData_1))
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository.delete(any[String], any[UUID]) returns IO.pure(
          Left(ApiKeyDbError.apiKeyDataNotFoundError(userId_1, publicKeyId_1))
        )

        managementService
          .deleteApiKeyBelongingToUserWith(userId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(userId_1, publicKeyId_1)))
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.delete(any[String], any[UUID]) returns IO.raiseError(testException)

        managementService
          .deleteApiKeyBelongingToUserWith(userId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on deleteApiKey" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.delete(any[UUID]) returns IO.pure(Right(apiKeyData_1))

      for {
        _ <- managementService.deleteApiKey(publicKeyId_1)
        _ = verify(apiKeyRepository).delete(eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.delete(any[UUID]) returns IO.pure(Right(apiKeyData_1))

        managementService.deleteApiKey(publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository.delete(any[UUID]) returns IO.pure(
          Left(ApiKeyDbError.apiKeyDataNotFoundError(publicKeyId_1))
        )

        managementService
          .deleteApiKey(publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.delete(any[UUID]) returns IO.raiseError(testException)

        managementService
          .deleteApiKey(publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on getAllApiKeysFor" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.getAll(any[String]) returns IO.pure(List(apiKeyData_1))

      for {
        _ <- managementService.getAllApiKeysFor(userId_1)
        _ = verify(apiKeyRepository).getAll(eqTo(userId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.getAll(any[String]) returns IO.pure(List(apiKeyData_1, apiKeyData_1, apiKeyData_1))

      managementService.getAllApiKeysFor(userId_1).asserting(_ shouldBe List(apiKeyData_1, apiKeyData_1, apiKeyData_1))
    }
  }

  "ManagementService on getApiKey" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.get(any[String], any[UUID]) returns IO.pure(Some(apiKeyData_1))

      for {
        _ <- managementService.getApiKey(userId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).get(eqTo(userId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.get(any[String], any[UUID]) returns IO.pure(Some(apiKeyData_1))

      managementService.getApiKey(userId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
    }
  }

  "ManagementService on getAllUserIds" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.getAllUserIds returns IO.pure(List(userId_1, userId_2))

      for {
        _ <- managementService.getAllUserIds
        _ = verify(apiKeyRepository).getAllUserIds
      } yield ()
    }

    "return the value returned by ApiKeyRepository" in {
      apiKeyRepository.getAllUserIds returns IO.pure(List(userId_1, userId_2))

      managementService.getAllUserIds.asserting(_ shouldBe List(userId_1, userId_2))
    }
  }
}
