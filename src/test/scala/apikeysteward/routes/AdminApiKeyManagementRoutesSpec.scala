package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError.ApiKeyIdAlreadyExistsError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.{ApiKeyDataNotFoundError, ApiKeyNotFoundError}
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer}
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.apikey._
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.TtlTooLargeError
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import io.circe.syntax.EncoderOps
import org.http4s.AuthScheme.Bearer
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Headers, HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class AdminApiKeyManagementRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val managementService = mock[ApiKeyManagementService]

  private val adminRoutes: HttpApp[IO] =
    new AdminApiKeyManagementRoutes(jwtAuthorizer, managementService).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, managementService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO])(requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminRoutes, jwtAuthorizer, managementService)(request, requiredPermissions)

  "AdminApiKeyRoutes on POST /admin/api-keys" when {

    val uri = Uri.unsafeFromString(s"/admin/api-keys")
    val requestBody = CreateApiKeyAdminRequest(
      userId = userId_1,
      name = name,
      description = description,
      ttl = ttlMinutes
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty name" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with name containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with name longer than 250 characters" should {

        val nameThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 250, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with description containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(description = Some("  \n   \n\n ")))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"Invalid value for: body (expected description to pass validation, but got: Some())")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with description longer than 250 characters" should {

        val descriptionThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(description = Some(descriptionThatIsTooLong)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"Invalid value for: body (expected description to pass validation, but got: Some($descriptionThatIsTooLong))"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with empty userId" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(userId = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected userId to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with userId containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(userId = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected userId to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with userId longer than 250 characters" should {

        val userIdThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(userId = userIdThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected userId to have length less than or equal to 250, but got: "$userIdThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with negative ttl value" should {

        val requestWithNegativeTtl = request.withEntity(requestBody.copy(ttl = -1))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected ttl to be greater than or equal to 0, but got -1)")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithNegativeTtl)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithNegativeTtl)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ManagementService" in authorizedFixture {
        managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
          (apiKey_1, apiKeyData_1).asRight
        )

        for {
          _ <- adminRoutes.run(request)

          expectedRequest = CreateApiKeyRequest(
            name = requestBody.name,
            description = requestBody.description,
            ttl = requestBody.ttl
          )
          _ = verify(managementService).createApiKey(eqTo(userId_1), eqTo(expectedRequest))
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "provided with description" in authorizedFixture {
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
            (apiKey_1, apiKeyData_1).asRight
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyResponse]
              .asserting(_ shouldBe CreateApiKeyResponse(apiKey_1.value, apiKeyData_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val apiKeyDataWithoutDescription = apiKeyData_1.copy(description = None)
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
            (apiKey_1, apiKeyDataWithoutDescription).asRight
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyResponse]
              .asserting(_ shouldBe CreateApiKeyResponse(apiKey_1.value, apiKeyDataWithoutDescription))
          } yield ()
        }
      }

      "return Bad Request when ManagementService returns successful IO with Left containing ValidationError" in authorizedFixture {
        val error =
          ValidationError(Seq(TtlTooLargeError(requestBody.ttl, FiniteDuration(ttlMinutes - 1, TimeUnit.MINUTES))))
        managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(Left(error))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(error.message)))
        } yield ()
      }

      "return Internal Server Error when ManagementService returns successful IO with Left containing InsertionError" in authorizedFixture {
        managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
          Left(InsertionError(ApiKeyIdAlreadyExistsError))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ManagementService returns failed IO" in authorizedFixture {
        managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyRoutes on PUT /admin/api-keys/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/admin/api-keys/$publicKeyId_1")
    val requestBody = UpdateApiKeyAdminRequest(name = name, description = description)

    val request = Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with publicKeyId which is not an UUID" should {

      val uri = Uri.unsafeFromString(s"/admin/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId =
        Request[IO](method = Method.PUT, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty name" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with name containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = "  \n   \n\n "))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with name longer than 250 characters" should {

        val nameThatIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 250, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with description containing only white characters" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(description = Some("  \n   \n\n ")))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"Invalid value for: body (expected description to pass validation, but got: Some())")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }

        "request body is provided with description longer than 250 characters" should {

          val descriptionThatIsTooLong = List.fill(251)("A").mkString
          val requestWithLongName = request.withEntity(requestBody.copy(description = Some(descriptionThatIsTooLong)))
          val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
            Some(
              s"Invalid value for: body (expected description to pass validation, but got: Some($descriptionThatIsTooLong))"
            )
          )

          "return Bad Request" in authorizedFixture {
            for {
              response <- adminRoutes.run(requestWithLongName)
              _ = response.status shouldBe Status.BadRequest
              _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
            } yield ()
          }

          "NOT call ManagementService" in authorizedFixture {
            for {
              _ <- adminRoutes.run(requestWithLongName)
              _ = verifyZeroInteractions(managementService)
            } yield ()
          }
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ManagementService" in authorizedFixture {
        managementService.updateApiKey(any[UUID], any[UpdateApiKeyAdminRequest]) returns IO.pure(
          apiKeyData_1.asRight
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).updateApiKey(eqTo(publicKeyId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "provided with description" in authorizedFixture {
          managementService.updateApiKey(any[UUID], any[UpdateApiKeyAdminRequest]) returns IO.pure(
            apiKeyData_1.asRight
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[UpdateApiKeyAdminResponse]
              .asserting(_ shouldBe UpdateApiKeyAdminResponse(apiKeyData_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val apiKeyDataWithoutDescription = apiKeyData_1.copy(description = None)
          managementService.updateApiKey(any[UUID], any[UpdateApiKeyAdminRequest]) returns IO.pure(
            apiKeyDataWithoutDescription.asRight
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[UpdateApiKeyAdminResponse]
              .asserting(_ shouldBe UpdateApiKeyAdminResponse(apiKeyDataWithoutDescription))
          } yield ()
        }
      }

      "return Not Found when ManagementService returns successful IO with Left containing ApiKeyDataNotFoundError" in authorizedFixture {
        val error = ApiKeyDbError.ApiKeyDataNotFoundError(userId_1, publicKeyIdStr_1)
        managementService.updateApiKey(any[UUID], any[UpdateApiKeyAdminRequest]) returns IO.pure(
          Left(error)
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKey.ApiKeyNotFound)))
        } yield ()
      }

      "return Internal Server Error when ManagementService returns failed IO" in authorizedFixture {
        managementService.updateApiKey(any[UUID], any[UpdateApiKeyAdminRequest]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyRoutes on GET /admin/users/{userId}/api-keys" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-keys")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadAdmin))

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ManagementService" in authorizedFixture {
        managementService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).getAllApiKeysFor(eqTo(userId_1))
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "ManagementService returns empty List" in authorizedFixture {
          managementService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[GetMultipleApiKeysResponse].asserting(_ shouldBe GetMultipleApiKeysResponse(List.empty))
          } yield ()
        }

        "ManagementService returns a List with several elements" in authorizedFixture {
          managementService.getAllApiKeysFor(any[String]) returns IO.pure(
            List(apiKeyData_1, apiKeyData_2, apiKeyData_3)
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeysResponse]
              .asserting(_ shouldBe GetMultipleApiKeysResponse(List(apiKeyData_1, apiKeyData_2, apiKeyData_3)))
          } yield ()
        }
      }

      "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
        managementService.getAllApiKeysFor(any[String]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminApiKeyRoutes on GET /admin/api-keys/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/admin/api-keys/$publicKeyId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadAdmin))

    "provided with publicKeyId which is not an UUID" should {

      val uri = Uri.unsafeFromString(s"/admin/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId =
        Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ManagementService" in authorizedFixture {
        managementService.getApiKey(any[UUID]) returns IO.pure(Some(apiKeyData_1))

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).getApiKey(eqTo(publicKeyId_1))
        } yield ()
      }

      "return successful value returned by ManagementService" in authorizedFixture {
        managementService.getApiKey(any[UUID]) returns IO.pure(Some(apiKeyData_1))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[GetSingleApiKeyResponse].asserting(_ shouldBe GetSingleApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when ManagementService returns empty Option" in authorizedFixture {
        managementService.getApiKey(any[UUID]) returns IO.pure(None)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKey.ApiKeyNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
        managementService.getApiKey(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }

  }

  "AdminApiKeyRoutes on DELETE /admin/api-keys/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/admin/api-keys/$publicKeyId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    "provided with publicKeyId which is not an UUID" should {

      val uri = Uri.unsafeFromString(s"/admin/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId =
        Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ManagementService" in authorizedFixture {
        managementService.deleteApiKey(any[UUID]) returns IO.pure(Right(apiKeyData_1))

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).deleteApiKey(eqTo(publicKeyId_1))
        } yield ()
      }

      "return Ok and ApiKeyData returned by ManagementService" in authorizedFixture {
        managementService.deleteApiKey(any[UUID]) returns IO.pure(Right(apiKeyData_1))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[DeleteApiKeyResponse].asserting(_ shouldBe DeleteApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when ManagementService returns Left containing ApiKeyDataNotFoundError" in authorizedFixture {
        managementService.deleteApiKey(any[UUID]) returns IO.pure(
          Left(ApiKeyDataNotFoundError(userId_1, publicKeyId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminApiKey.ApiKeyNotFound)))
        } yield ()
      }

      "return Internal Server Error when ManagementService returns Left containing ApiKeyNotFoundError" in authorizedFixture {
        managementService.deleteApiKey(any[UUID]) returns IO.pure(Left(ApiKeyNotFoundError))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
        managementService.deleteApiKey(any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }
}
