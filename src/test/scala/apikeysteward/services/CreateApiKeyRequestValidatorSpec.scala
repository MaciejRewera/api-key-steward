package apikeysteward.services

import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, user_1}
import apikeysteward.model.ApiKeyData
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.{ApiKeyTemplateRepository, UserRepository}
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import scala.concurrent.duration.Duration

class CreateApiKeyRequestValidatorSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with EitherValues {

  private val userRepository           = mock[UserRepository]
  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]

  private val requestValidator =
    new CreateApiKeyRequestValidator(userRepository, apiKeyTemplateRepository)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    reset(userRepository, apiKeyTemplateRepository)
  }

  private val apiKeyTemplate = apiKeyTemplate_1.copy(permissions = List(permission_1, permission_2, permission_3))

  private val createRequest: CreateApiKeyRequest = CreateApiKeyRequest(
    name = name_1,
    description = description_1,
    ttl = apiKeyMaxExpiryPeriod_1.minus(Duration(1, ApiKeyData.ApiKeyTtlResolution)),
    templateId = publicTemplateId_1,
    permissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
  )

  val testException = new RuntimeException("Test Exception")

  "CreateApiKeyRequestValidator on validateCreateRequest" when {

    "everything works correctly" should {

      def initMocks(): Unit = {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
        apiKeyTemplateRepository
          .getAllForUser(any[TenantId], any[UserId])
          .returns(IO.pure(List(apiKeyTemplate, apiKeyTemplate_2)))
      }

      "call UserRepository and ApiKeyTemplateRepository" in {
        initMocks()

        for {
          _ <- requestValidator.validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)

          _ = verify(userRepository).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(apiKeyTemplateRepository).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return Right containing CreateApiKeyRequest" when {

        "the request contains a single permission that is defined in the ApiKeyTemplate" in {
          initMocks()
          val request = createRequest.copy(permissionIds = List(publicPermissionId_1))

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, request)
            .asserting(_ shouldBe Right(request))
        }

        "the request contains several permissions and all are defined in the ApiKeyTemplate" in {
          initMocks()
          val request = createRequest.copy(permissionIds = List(publicPermissionId_1, publicPermissionId_3))

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, request)
            .asserting(_ shouldBe Right(request))
        }

        "the request contains the same permissions as defined in the ApiKeyTemplate" in {
          initMocks()

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)
            .asserting(_ shouldBe Right(createRequest))
        }

        "the request contains ttl value smaller than ApiKeyTemplate.apiKeyMaxExpiryPeriod" in {
          initMocks()

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)
            .asserting(_ shouldBe Right(createRequest))
        }

        "the request contains ttl value equal to ApiKeyTemplate.apiKeyMaxExpiryPeriod" in {
          initMocks()

          val request = createRequest.copy(ttl = apiKeyMaxExpiryPeriod_1)

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, request)
            .asserting(_ shouldBe Right(request))
        }
      }

      "return Left containing appropriate CreateApiKeyRequestValidatorError" when {

        "the request contains templateId which is NOT assigned to provided User" in {
          userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
          apiKeyTemplateRepository
            .getAllForUser(any[TenantId], any[UserId])
            .returns(IO.pure(List(apiKeyTemplate_2, apiKeyTemplate_3)))

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)
            .asserting(
              _ shouldBe Left(
                ApiKeyTemplateNotAssignedToUserError(publicTenantId_1, publicUserId_1, publicTemplateId_1)
              )
            )
        }

        "the request contains a permission that is NOT defined in the ApiKeyTemplate" in {
          userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
          apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyTemplate)))

          val request = createRequest.copy(permissionIds = List(publicPermissionId_4))

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, request)
            .asserting(
              _ shouldBe Left(
                PermissionsNotAllowedError(publicTenantId_1, publicTemplateId_1, List(publicPermissionId_4))
              )
            )
        }

        "the request contains several permissions, one of which is NOT defined in the ApiKeyTemplate" in {
          userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
          apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyTemplate)))

          val request =
            createRequest.copy(permissionIds = List(publicPermissionId_1, publicPermissionId_3, publicPermissionId_4))

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, request)
            .asserting(
              _ shouldBe Left(
                PermissionsNotAllowedError(publicTenantId_1, publicTemplateId_1, List(publicPermissionId_4))
              )
            )
        }

        "the request contains ttl value bigger than ApiKeyTemplate.apiKeyMaxExpiryPeriod" in {
          initMocks()
          val requestTtl = apiKeyMaxExpiryPeriod_1.plus(Duration(1, ApiKeyData.ApiKeyTtlResolution))
          val request    = createRequest.copy(ttl = requestTtl)

          requestValidator
            .validateCreateRequest(publicTenantId_1, publicUserId_1, request)
            .asserting(_ shouldBe Left(TtlTooLargeError(ttlRequest = requestTtl, ttlMax = apiKeyMaxExpiryPeriod_1)))
        }
      }
    }

    "UserRepository returns empty Option" should {

      "call ApiKeyTemplateRepository anyway" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(None))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyTemplate)))

        for {
          _ <- requestValidator.validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)

          _ = verify(apiKeyTemplateRepository).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return Left containing appropriate CreateApiKeyRequestValidatorError" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(None))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyTemplate)))

        requestValidator
          .validateCreateRequest(publicTenantId_1, publicUserId_2, createRequest)
          .asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_1, publicUserId_2)))
      }
    }

    "UserRepository returns failed IO" should {

      "call ApiKeyTemplateRepository anyway" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.raiseError(testException))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyTemplate)))

        for {
          _ <- requestValidator.validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest).attempt

          _ = verify(apiKeyTemplateRepository).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return failed IO containing the same exception" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.raiseError(testException))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List(apiKeyTemplate)))

        requestValidator
          .validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateRepository returns empty List" should {

      "call UserRepository anyway" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List.empty))

        for {
          _ <- requestValidator.validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)

          _ = verify(userRepository).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return Left containing appropriate CreateApiKeyRequestValidatorError" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(List.empty))

        requestValidator
          .validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)
          .asserting(
            _ shouldBe Left(ApiKeyTemplateNotAssignedToUserError(publicTenantId_1, publicUserId_1, publicTemplateId_1))
          )
      }
    }

    "ApiKeyTemplateRepository returns failed IO" should {

      "call UserRepository anyway" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.raiseError(testException))

        for {
          _ <- requestValidator.validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest).attempt

          _ = verify(userRepository).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return failed IO containing the same exception" in {
        userRepository.getBy(any[TenantId], any[UserId]).returns(IO.pure(Some(user_1)))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]).returns(IO.raiseError(testException))

        requestValidator
          .validateCreateRequest(publicTenantId_1, publicUserId_1, createRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
