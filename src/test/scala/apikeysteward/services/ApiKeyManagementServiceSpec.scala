package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, user_1}
import apikeysteward.generators.ApiKeyGenerator
import apikeysteward.generators.ApiKeyGenerator.ApiKeyGeneratorError
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.CommonError.UserDoesNotExistError
import apikeysteward.model.errors.PermissionDbError.PermissionNotFoundError
import apikeysteward.model.errors.{ApiKeyDbError, CustomError}
import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate}
import apikeysteward.repositories.{ApiKeyRepository, PermissionRepository, UserRepository}
import apikeysteward.routes.model.admin.apikey.UpdateApiKeyAdminRequest
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{
  ApiKeyCreateErrorImpl,
  InsertionError,
  ValidationError
}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.TtlTooLargeError
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, none}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyNoMoreInteractions, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration.Duration

class ApiKeyManagementServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator                = mock[UuidGenerator]
  private val createApiKeyRequestValidator = mock[CreateApiKeyRequestValidator]
  private val apiKeyGenerator              = mock[ApiKeyGenerator]
  private val apiKeyRepository             = mock[ApiKeyRepository]
  private val userRepository               = mock[UserRepository]
  private val permissionRepository         = mock[PermissionRepository]

  private val managementService = new ApiKeyManagementService(
    createApiKeyRequestValidator,
    apiKeyGenerator,
    uuidGenerator,
    apiKeyRepository,
    userRepository,
    permissionRepository
  )

  private val createApiKeyRequest = CreateApiKeyRequest(
    name = name_1,
    description = description_1,
    templateId = publicTemplateId_1,
    ttl = ttl,
    permissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
  )

  private val testApiKeyData = apiKeyData_1.copy(permissions = List(permission_1, permission_2, permission_3))

  override def beforeEach(): Unit = {
    reset(
      createApiKeyRequestValidator,
      apiKeyGenerator,
      uuidGenerator,
      apiKeyRepository,
      userRepository,
      permissionRepository
    )

    createApiKeyRequestValidator
      .validateCreateRequest(any[TenantId], any[UserId], any[CreateApiKeyRequest])
      .returns(IO.pure(Right(createApiKeyRequest)))
    apiKeyGenerator.generateApiKey(any[TenantId], any[ApiKeyTemplateId]).returns(IO.pure(Right(apiKey_1)))
    uuidGenerator.generateUuid.returns(IO.pure(publicKeyId_1))
    apiKeyRepository
      .insert(any[TenantId], any[ApiKey], any[ApiKeyData], any[ApiKeyTemplateId])
      .returns(IO.pure(Right(testApiKeyData)))

    permissionRepository
      .getBy(any[TenantId], any[List[PermissionId]])
      .returns(IO.pure(List(permission_1, permission_2, permission_3).asRight[PermissionNotFoundError]))
  }

  private val testException = new RuntimeException("Test Exception")

  "ManagementService on createApiKey" when {

    "everything works correctly" should {

      "call CreateApiKeyRequestValidator, ApiKeyGenerator, UuidGenerator, ApiKeyRepository and PermissionRepository providing correct ApiKeyData" in {
        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)

          _ = verify(createApiKeyRequestValidator).validateCreateRequest(
            eqTo(publicTenantId_1),
            eqTo(publicUserId_1),
            eqTo(createApiKeyRequest)
          )
          _ = verify(apiKeyGenerator).generateApiKey(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(uuidGenerator).generateUuid
          _ = verify(apiKeyRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(apiKey_1),
            eqTo(testApiKeyData),
            eqTo(publicTemplateId_1)
          )
          _ = verify(permissionRepository).getBy(
            eqTo(publicTenantId_1),
            eqTo(List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3))
          )
        } yield ()
      }

      "return the newly created Api Key together with the ApiKeyData returned by ApiKeyRepository" in
        managementService
          .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          .asserting(_ shouldBe Right(apiKey_1 -> testApiKeyData))
    }

    "CreateApiKeyRequestValidator returns Left" should {

      val error = TtlTooLargeError(ttl, ttl.minus(Duration(1, ttl.unit)))

      "NOT call ApiKeyGenerator, UuidGenerator or ApiKeyRepository" in {
        createApiKeyRequestValidator
          .validateCreateRequest(
            any[TenantId],
            any[UserId],
            any[CreateApiKeyRequest]
          )
          .returns(IO.pure(Left(error)))

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)

          _ = verifyNoInteractions(apiKeyGenerator, uuidGenerator, apiKeyRepository)
        } yield ()
      }

      "return successful IO containing Left with ValidationError" in {
        createApiKeyRequestValidator
          .validateCreateRequest(
            any[TenantId],
            any[UserId],
            any[CreateApiKeyRequest]
          )
          .returns(IO.pure(Left(error)))

        managementService
          .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          .asserting(_ shouldBe Left(ValidationError(error)))
      }
    }

    "ApiKeyGenerator returns Left containing error" should {

      val customError = new CustomError { override val message = "Test CustomError" }

      "NOT call ApiKeyGenerator again" in {
        apiKeyGenerator
          .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
          .returns(
            IO.pure(
              Left(ApiKeyGeneratorError(customError))
            )
          )

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          _ = verify(apiKeyGenerator).generateApiKey(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verifyNoMoreInteractions(apiKeyGenerator)
        } yield ()
      }

      "NOT call UuidGenerator or ApiKeyRepository" in {
        apiKeyGenerator
          .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
          .returns(
            IO.pure(
              Left(ApiKeyGeneratorError(customError))
            )
          )

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          _ = verifyNoInteractions(uuidGenerator, apiKeyRepository)
        } yield ()
      }

      "return Left containing ApiKeyCreateErrorImpl" in {
        apiKeyGenerator
          .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
          .returns(
            IO.pure(
              Left(ApiKeyGeneratorError(customError))
            )
          )

        managementService
          .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          .asserting(_ shouldBe Left(ApiKeyCreateErrorImpl(ApiKeyGeneratorError(customError))))
      }
    }

    "ApiKeyGenerator returns failed IO" should {

      "NOT call ApiKeyGenerator again" in {
        apiKeyGenerator.generateApiKey(any[TenantId], any[ApiKeyTemplateId]).returns(IO.raiseError(testException))

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest).attempt
          _ = verify(apiKeyGenerator).generateApiKey(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verifyNoMoreInteractions(apiKeyGenerator)
        } yield ()
      }

      "NOT call UuidGenerator or ApiKeyRepository" in {
        apiKeyGenerator.generateApiKey(any[TenantId], any[ApiKeyTemplateId]).returns(IO.raiseError(testException))

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest).attempt
          _ = verifyNoInteractions(uuidGenerator, apiKeyRepository)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyGenerator.generateApiKey(any[TenantId], any[ApiKeyTemplateId]).returns(IO.raiseError(testException))

        managementService
          .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call UuidGenerator again" in {
        uuidGenerator.generateUuid.returns(IO.raiseError(testException))

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest).attempt
          _ = verify(uuidGenerator).generateUuid
          _ = verifyNoMoreInteractions(uuidGenerator)
        } yield ()
      }

      "NOT call ApiKeyRepository" in {
        uuidGenerator.generateUuid.returns(IO.raiseError(testException))

        for {
          _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest).attempt
          _ = verifyNoInteractions(apiKeyRepository)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        uuidGenerator.generateUuid.returns(IO.raiseError(testException))

        managementService
          .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    Seq(ApiKeyAlreadyExistsError, PublicKeyIdAlreadyExistsError).foreach { insertionError =>
      s"ApiKeyRepository returns ${insertionError.getClass.getSimpleName} on the first try" should {

        "call ApiKeyGenerator, UuidGenerator and ApiKeyRepository again" in {
          apiKeyGenerator
            .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
            .returns(
              IO.pure(Right(apiKey_1)),
              IO.pure(Right(apiKey_2))
            )
          uuidGenerator.generateUuid.returns(IO.pure(publicKeyId_1), IO.pure(publicKeyId_2))
          apiKeyRepository
            .insert(any[TenantId], any[ApiKey], any[ApiKeyData], any[ApiKeyTemplateId])
            .returns(
              IO.pure(Left(insertionError)),
              IO.pure(Right(testApiKeyData))
            )

          for {
            _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
            _ = verify(apiKeyGenerator, times(2)).generateApiKey(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
            _ = verify(uuidGenerator, times(2)).generateUuid

            _ = {
              val captor_1: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(
                eqTo(publicTenantId_1),
                eqTo(apiKey_1),
                captor_1.capture(),
                eqTo(publicTemplateId_1)
              )
              val actualApiKeyData_1: ApiKeyData = captor_1.getValue
              actualApiKeyData_1 shouldBe testApiKeyData.copy(publicKeyId = actualApiKeyData_1.publicKeyId)

              val captor_2: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(
                eqTo(publicTenantId_1),
                eqTo(apiKey_2),
                captor_2.capture(),
                eqTo(publicTemplateId_1)
              )
              val actualApiKeyData_2: ApiKeyData = captor_2.getValue
              actualApiKeyData_2 shouldBe testApiKeyData.copy(publicKeyId = actualApiKeyData_2.publicKeyId)
            }
          } yield ()
        }

        "return the second created Api Key together with the ApiKeyData returned by ApiKeyRepository" in {
          apiKeyGenerator
            .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
            .returns(
              IO.pure(Right(apiKey_1)),
              IO.pure(Right(apiKey_2))
            )
          uuidGenerator.generateUuid.returns(IO.pure(publicKeyId_1), IO.pure(publicKeyId_2))
          apiKeyRepository
            .insert(any[TenantId], any[ApiKey], any[ApiKeyData], any[ApiKeyTemplateId])
            .returns(
              IO.pure(Left(insertionError)),
              IO.pure(Right(testApiKeyData))
            )

          managementService
            .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
            .asserting(_ shouldBe Right(apiKey_2 -> testApiKeyData))
        }
      }
    }

    Seq(ApiKeyAlreadyExistsError, PublicKeyIdAlreadyExistsError).foreach { dbInsertionError =>
      s"ApiKeyRepository keeps returning ${dbInsertionError.getClass.getSimpleName}" should {

        "call ApiKeyGenerator and ApiKeyRepository again until reaching max retries amount (3)" in {
          apiKeyGenerator
            .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
            .returns(
              IO.pure(Right(apiKey_1)),
              IO.pure(Right(apiKey_2)),
              IO.pure(Right(apiKey_3)),
              IO.pure(Right(apiKey_4))
            )
          uuidGenerator.generateUuid.returns(
            IO.pure(publicKeyId_1),
            IO.pure(publicKeyId_2),
            IO.pure(publicKeyId_3),
            IO.pure(publicKeyId_4)
          )
          apiKeyRepository
            .insert(any[TenantId], any[ApiKey], any[ApiKeyData], any[ApiKeyTemplateId])
            .returns(
              IO.pure(
                Left(dbInsertionError)
              )
            )

          for {
            _ <- managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest).attempt
            _ = verify(apiKeyGenerator, times(4)).generateApiKey(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
            _ = verify(uuidGenerator, times(4)).generateUuid

            _ = {
              val captor_1: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(
                eqTo(publicTenantId_1),
                eqTo(apiKey_1),
                captor_1.capture(),
                eqTo(publicTemplateId_1)
              )
              val actualApiKeyData_1: ApiKeyData = captor_1.getValue
              actualApiKeyData_1 shouldBe testApiKeyData.copy(publicKeyId = actualApiKeyData_1.publicKeyId)

              val captor_2: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(
                eqTo(publicTenantId_1),
                eqTo(apiKey_2),
                captor_2.capture(),
                eqTo(publicTemplateId_1)
              )
              val actualApiKeyData_2: ApiKeyData = captor_2.getValue
              actualApiKeyData_2 shouldBe testApiKeyData.copy(publicKeyId = actualApiKeyData_2.publicKeyId)

              val captor_3: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(
                eqTo(publicTenantId_1),
                eqTo(apiKey_3),
                captor_3.capture(),
                eqTo(publicTemplateId_1)
              )
              val actualApiKeyData_3: ApiKeyData = captor_3.getValue
              actualApiKeyData_3 shouldBe testApiKeyData.copy(publicKeyId = actualApiKeyData_3.publicKeyId)

              val captor_4: ArgumentCaptor[ApiKeyData] = ArgumentCaptor.forClass(classOf[ApiKeyData])
              verify(apiKeyRepository).insert(
                eqTo(publicTenantId_1),
                eqTo(apiKey_4),
                captor_4.capture(),
                eqTo(publicTemplateId_1)
              )
              val actualApiKeyData_4: ApiKeyData = captor_4.getValue
              actualApiKeyData_4 shouldBe testApiKeyData.copy(publicKeyId = actualApiKeyData_4.publicKeyId)
            }
          } yield ()
        }

        "return successful IO containing Left with InsertionError" in {
          apiKeyGenerator
            .generateApiKey(any[TenantId], any[ApiKeyTemplateId])
            .returns(
              IO.pure(Right(apiKey_1)),
              IO.pure(Right(apiKey_2)),
              IO.pure(Right(apiKey_3)),
              IO.pure(Right(apiKey_4))
            )
          uuidGenerator.generateUuid.returns(
            IO.pure(publicKeyId_1),
            IO.pure(publicKeyId_2),
            IO.pure(publicKeyId_3),
            IO.pure(publicKeyId_4)
          )
          apiKeyRepository
            .insert(any[TenantId], any[ApiKey], any[ApiKeyData], any[ApiKeyTemplateId])
            .returns(
              IO.pure(
                Left(dbInsertionError)
              )
            )

          managementService.createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest).asserting { result =>
            result shouldBe Left(InsertionError(dbInsertionError))
          }
        }
      }
    }

    "ApiKeyRepository returns a different exception" should {
      "return IO with this exception" in {
        apiKeyGenerator.generateApiKey(any[TenantId], any[ApiKeyTemplateId]).returns(IO.pure(Right(apiKey_1)))
        apiKeyRepository
          .insert(any[TenantId], any[ApiKey], any[ApiKeyData], any[ApiKeyTemplateId])
          .returns(
            IO
              .raiseError(testException)
          )

        managementService
          .createApiKey(publicTenantId_1, publicUserId_1, createApiKeyRequest)
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
      publicUserId = publicUserId_1,
      expiresAt = ApiKeyExpirationCalculator.calcExpiresAtFromNow(ttl),
      permissions = List(permission_1, permission_2)
    )

    "call ApiKeyRepository" in {
      apiKeyRepository.update(any[TenantId], any[ApiKeyDataUpdate]).returns(IO.pure(Right(outputApiKeyData)))

      for {
        _ <- managementService.updateApiKey(publicTenantId_1, publicKeyId_1, updateApiKeyRequest)
        _ = verify(apiKeyRepository).update(eqTo(publicTenantId_1), eqTo(apiKeyDataUpdate_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.update(any[TenantId], any[ApiKeyDataUpdate]).returns(IO.pure(Right(outputApiKeyData)))

        managementService
          .updateApiKey(publicTenantId_1, publicKeyId_1, updateApiKeyRequest)
          .asserting(
            _ shouldBe Right(outputApiKeyData)
          )
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository
          .update(any[TenantId], any[ApiKeyDataUpdate])
          .returns(
            IO.pure(
              Left(ApiKeyDbError.ApiKeyDataNotFoundError(publicUserId_1, publicKeyIdStr_1))
            )
          )

        managementService
          .updateApiKey(publicTenantId_1, publicKeyId_1, updateApiKeyRequest)
          .asserting(
            _ shouldBe Left(ApiKeyDbError.ApiKeyDataNotFoundError(publicUserId_1, publicKeyIdStr_1))
          )
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.update(any[TenantId], any[ApiKeyDataUpdate]).returns(IO.raiseError(testException))

        managementService
          .updateApiKey(publicTenantId_1, publicKeyId_1, updateApiKeyRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on deleteApiKeyBelongingToUserWith" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.delete(any[TenantId], any[String], any[ApiKeyId]).returns(IO.pure(Right(apiKeyData_1)))

      for {
        _ <- managementService.deleteApiKeyBelongingToUserWith(publicTenantId_1, publicUserId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).delete(eqTo(publicTenantId_1), eqTo(publicUserId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.delete(any[TenantId], any[String], any[ApiKeyId]).returns(IO.pure(Right(apiKeyData_1)))

        managementService
          .deleteApiKeyBelongingToUserWith(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Right(apiKeyData_1))
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository
          .delete(any[TenantId], any[String], any[ApiKeyId])
          .returns(
            IO.pure(
              Left(ApiKeyDbError.apiKeyDataNotFoundError(publicUserId_1, publicKeyId_1))
            )
          )

        managementService
          .deleteApiKeyBelongingToUserWith(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)))
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.delete(any[TenantId], any[String], any[ApiKeyId]).returns(IO.raiseError(testException))

        managementService
          .deleteApiKeyBelongingToUserWith(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on deleteApiKey" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.delete(any[TenantId], any[ApiKeyId]).returns(IO.pure(Right(apiKeyData_1)))

      for {
        _ <- managementService.deleteApiKey(publicTenantId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns Right" in {
        apiKeyRepository.delete(any[TenantId], any[ApiKeyId]).returns(IO.pure(Right(apiKeyData_1)))

        managementService.deleteApiKey(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
      }

      "ApiKeyRepository returns Left" in {
        apiKeyRepository
          .delete(any[TenantId], any[ApiKeyId])
          .returns(
            IO.pure(
              Left(ApiKeyDbError.apiKeyDataNotFoundError(publicKeyId_1))
            )
          )

        managementService
          .deleteApiKey(publicTenantId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.delete(any[TenantId], any[ApiKeyId]).returns(IO.raiseError(testException))

        managementService
          .deleteApiKey(publicTenantId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on getAllForUser" when {

    "everything works correctly" should {

      "call UserRepository and ApiKeyRepository" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Option(user_1)))
        apiKeyRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyData_1)))

        for {
          _ <- managementService.getAllForUser(publicTenantId_1, publicUserId_1)

          _ = verify(userRepository).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(apiKeyRepository).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return the value returned by ApiKeyRepository" when {

        "ApiKeyRepository returns empty List" in {
          userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Option(user_1)))
          apiKeyRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List.empty))

          managementService
            .getAllForUser(publicTenantId_1, publicUserId_1)
            .asserting(_ shouldBe Right(List.empty[ApiKeyData]))
        }

        "ApiKeyRepository returns List with elements" in {
          userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Option(user_1)))
          apiKeyRepository
            .getAllForUser(any[TenantId], any[UserId])
            .returns(
              IO.pure(
                List(apiKeyData_1, apiKeyData_1, apiKeyData_1)
              )
            )

          managementService
            .getAllForUser(publicTenantId_1, publicUserId_1)
            .asserting(_ shouldBe Right(List(apiKeyData_1, apiKeyData_1, apiKeyData_1)))
        }
      }
    }

    "UserRepository returns empty Option" should {

      "NOT call ApiKeyRepository" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Option.empty))

        for {
          _ <- managementService.getAllForUser(publicTenantId_1, publicUserId_1)

          _ = verifyZeroInteractions(apiKeyRepository)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Option.empty))

        managementService
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe Left(UserDoesNotExistError(publicTenantId_1, publicUserId_1)))
      }
    }

    "ApiKeyRepository returns failed IO" should {
      "return failed IO containing the same exception" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Option(user_1)))
        apiKeyRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.raiseError(testException))

        managementService
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on getApiKey(:userId, :publicKeyId)" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.get(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.pure(Some(apiKeyData_1)))

      for {
        _ <- managementService.getApiKey(publicTenantId_1, publicUserId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).get(eqTo(publicTenantId_1), eqTo(publicUserId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns empty Option" in {
        apiKeyRepository.get(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.pure(None))

        managementService
          .getApiKey(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe none[ApiKeyData])
      }

      "ApiKeyRepository returns Option containing ApiKeyData" in {
        apiKeyRepository.get(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.pure(Some(apiKeyData_1)))

        managementService
          .getApiKey(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Some(apiKeyData_1))
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.get(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.raiseError(testException))

        managementService
          .getApiKey(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ManagementService on getApiKey(:publicKeyId)" should {

    "call ApiKeyRepository" in {
      apiKeyRepository.getByPublicKeyId(any[TenantId], any[ApiKeyId]).returns(IO.pure(Some(apiKeyData_1)))

      for {
        _ <- managementService.getApiKey(publicTenantId_1, publicKeyId_1)
        _ = verify(apiKeyRepository).getByPublicKeyId(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return the value returned by ApiKeyRepository" when {

      "ApiKeyRepository returns empty Option" in {
        apiKeyRepository.getByPublicKeyId(any[TenantId], any[ApiKeyId]).returns(IO.pure(None))

        managementService.getApiKey(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe none[ApiKeyData])
      }

      "ApiKeyRepository returns Option containing ApiKeyData" in {
        apiKeyRepository.getByPublicKeyId(any[TenantId], any[ApiKeyId]).returns(IO.pure(Some(apiKeyData_1)))

        managementService.getApiKey(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
      }
    }

    "return failed IO" when {
      "ApiKeyRepository returns failed IO" in {
        apiKeyRepository.getByPublicKeyId(any[TenantId], any[ApiKeyId]).returns(IO.raiseError(testException))

        managementService
          .getApiKey(publicTenantId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
