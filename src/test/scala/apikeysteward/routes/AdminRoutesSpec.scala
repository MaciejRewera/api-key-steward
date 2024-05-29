package apikeysteward.routes

import apikeysteward.base.TestData._
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.{ApiKeyDataNotFound, GenericApiKeyDeletionError}
import apikeysteward.routes.auth.JwtValidator.{AccessToken, Permission}
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.auth.{AuthTestData, JwtValidator}
import apikeysteward.routes.definitions.ErrorMessages
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import io.circe.syntax.EncoderOps
import org.http4s.AuthScheme.Bearer
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.headers.Authorization
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Credentials, Headers, HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class AdminRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val jwtValidator = mock[JwtValidator]
  private val adminService = mock[AdminService]

  private val adminRoutes: HttpApp[IO] = new AdminRoutes(jwtValidator, adminService).allRoutes.orNotFound

  private val tokenString: AccessToken = "TOKEN"
  private val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(adminService, jwtValidator)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] = IO {
    jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
      AuthTestData.jwtWithMockedSignature.asRight
    )
  }.flatMap(_ => test)

  "AdminRoutes on POST /admin/users/{userId}/api-key" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key")
    val requestBody = CreateApiKeyRequest(
      name = name,
      description = description,
      ttl = ttlSeconds,
      scopes = List(scopeRead_1, scopeWrite_1)
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- adminRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtValidator or AdminService" in {
        for {
          _ <- adminRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, adminService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- adminRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.WriteAdmin)))(eqTo(tokenString))
      } yield ()
    }

    "JwtValidator returns Left containing error" should {

      val jwtValidatorError = ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken, but request body is incorrect" when {

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

        "NOT call AdminService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(adminService)
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

        "NOT call AdminService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(adminService)
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

        "NOT call AdminService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(adminService)
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

        "NOT call AdminService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(adminService)
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

          "NOT call AdminService" in authorizedFixture {
            for {
              _ <- adminRoutes.run(requestWithLongName)
              _ = verifyZeroInteractions(adminService)
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

          "NOT call AdminService" in authorizedFixture {
            for {
              _ <- adminRoutes.run(requestWithNegativeTtl)
              _ = verifyZeroInteractions(adminService)
            } yield ()
          }
        }
      }
    }

    "JwtValidator returns Right containing JsonWebToken and request body is correct" should {

      "call AdminService" in authorizedFixture {
        adminService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(adminService).createApiKey(eqTo(userId_1), eqTo(requestBody))
        } yield ()
      }

      "return the value returned by AdminService" when {

        "provided with description" in authorizedFixture {
          adminService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyResponse]
              .asserting(_ shouldBe CreateApiKeyResponse(apiKey_1, apiKeyData_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val apiKeyDataWithoutDescription = apiKeyData_1.copy(description = None)
          adminService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
            apiKey_1,
            apiKeyDataWithoutDescription
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateApiKeyResponse]
              .asserting(_ shouldBe CreateApiKeyResponse(apiKey_1, apiKeyDataWithoutDescription))
          } yield ()
        }
      }

      "return Internal Server Error when AdminService returns failed IO" in authorizedFixture {
        adminService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminRoutes on GET /admin/users/{userId}/api-key" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- adminRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtValidator or AdminService" in {
        for {
          _ <- adminRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, adminService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- adminRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.ReadAdmin)))(eqTo(tokenString))
      } yield ()
    }

    "JwtValidator returns Left containing error" should {

      val jwtValidatorError =
        ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken" should {

      "call AdminService" in authorizedFixture {
        adminService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(adminService).getAllApiKeysFor(eqTo(userId_1))
        } yield ()
      }

      "return Not Found when AdminService returns empty List" in authorizedFixture {
        adminService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.Admin.GetAllApiKeysForUserNotFound)))
        } yield ()
      }

      "return Ok and all ApiKeyData when AdminService returns non-empty List" in authorizedFixture {
        adminService.getAllApiKeysFor(any[String]) returns IO.pure(List(apiKeyData_1, apiKeyData_2, apiKeyData_3))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[List[ApiKeyData]].asserting(_ shouldBe List(apiKeyData_1, apiKeyData_2, apiKeyData_3))
        } yield ()
      }

      "return Internal Server Error when AdminService returns an exception" in authorizedFixture {
        adminService.getAllApiKeysFor(any[String]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminRoutes on GET /admin/users" when {

    val uri = uri"/admin/users"
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- adminRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtValidator or AdminService" in {
        for {
          _ <- adminRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, adminService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- adminRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.ReadAdmin)))(eqTo(tokenString))
      } yield ()
    }

    "JwtValidator returns Left containing error" should {

      val jwtValidatorError =
        ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken" should {

      "call AdminService" in authorizedFixture {
        adminService.getAllUserIds returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(adminService).getAllUserIds
        } yield ()
      }

      "return the value returned by AdminService" when {

        "it is an empty List" in authorizedFixture {
          adminService.getAllUserIds returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[List[String]].asserting(_ shouldBe List.empty[String])
          } yield ()
        }

        "it is a List with several elements" in authorizedFixture {
          adminService.getAllUserIds returns IO.pure(List(userId_1, userId_2, userId_3))

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[List[String]].asserting(_ shouldBe List(userId_1, userId_2, userId_3))
          } yield ()
        }
      }

      "return Internal Server Error when AdminService returns an exception" in authorizedFixture {
        adminService.getAllUserIds returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminRoutes on DELETE /admin/users/{userId}/api-key/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key/$publicKeyId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- adminRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtValidator or AdminService" in {
        for {
          _ <- adminRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, adminService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- adminRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.WriteAdmin)))(eqTo(tokenString))
      } yield ()
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns Left containing error" should {

      val jwtValidatorError =
        ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call AdminService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verifyZeroInteractions(adminService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken" should {

      "call AdminService" in authorizedFixture {
        adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

        for {
          _ <- adminRoutes.run(request)
          _ = verify(adminService).deleteApiKey(eqTo(userId_1), eqTo(publicKeyId_1))
        } yield ()
      }

      "return Ok and ApiKeyData returned by AdminService" in authorizedFixture {
        adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[DeleteApiKeyResponse].asserting(_ shouldBe DeleteApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when AdminService returns Left containing ApiKeyDataNotFound" in authorizedFixture {
        adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(
          Left(ApiKeyDataNotFound(userId_1, publicKeyId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.Admin.DeleteApiKeyNotFound)))
        } yield ()
      }

      "return Internal Server Error when AdminService returns Left containing GenericApiKeyDeletionError" in authorizedFixture {
        adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(
          Left(GenericApiKeyDeletionError(userId_1, publicKeyId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Bad Request when provided with publicKeyId which is not an UUID" in authorizedFixture {
        val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key/this-is-not-a-valid-uuid")
        val requestWithIncorrectPublicKeyId = Request[IO](method = Method.DELETE, uri = uri)

        for {
          response <- adminRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }

      "return Internal Server Error when AdminService returns an exception" in authorizedFixture {
        adminService.deleteApiKey(any[String], any[UUID]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }
}
