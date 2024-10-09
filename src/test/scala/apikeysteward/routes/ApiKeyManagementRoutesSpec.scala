package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError.ApiKeyIdAlreadyExistsError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.{ApiKeyDataNotFoundError, ApiKeyNotFoundError}
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.model.{JsonWebToken, JwtPermissions}
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer, JwtOps}
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.NotAllowedScopesProvidedError
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

class ApiKeyManagementRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val jwtOps = mock[JwtOps]
  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val managementService = mock[ApiKeyManagementService]

  private val managementRoutes: HttpApp[IO] =
    new ApiKeyManagementRoutes(jwtOps, jwtAuthorizer, managementService).allRoutes.orNotFound

  private val tokenString: AccessToken = "TOKEN"
  private val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  private val testException = new RuntimeException("Test Exception")
  private val jwtOpsTestError = ErrorInfo.unauthorizedErrorInfo(Some("JwtOps encountered an error."))

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwtOps, jwtAuthorizer, managementService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] = IO {
    jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
      AuthTestData.jwtWithMockedSignature.asRight
    )
    jwtOps.extractUserId(any[JsonWebToken]) returns AuthTestData.jwtWithMockedSignature.claim.subject.get.asRight
  }.flatMap(_ => test)

  private def runCommonJwtTests(request: Request[IO])(requiredPermissions: Set[Permission]): Unit = {

    "the JWT is NOT provided" should {

      val requestWithoutJwt = request.withHeaders(Headers.empty)

      "return Unauthorized" in {
        for {
          response <- managementRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtOps, JwtAuthorizer or ManagementService" in {
        for {
          _ <- managementRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtOps, jwtAuthorizer, managementService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtAuthorizer providing access token" in {
      jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- managementRoutes.run(request)
        _ = verify(jwtAuthorizer).authorisedWithPermissions(eqTo(requiredPermissions))(eqTo(tokenString))
      } yield ()
    }

    "JwtAuthorizer returns Left containing error" should {

      val jwtValidatorError = ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call either JwtOps or ManagementService" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }
    }

    "JwtAuthorizer returns failed IO" should {

      "return Internal Server Error" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call either JwtOps or ManagementService" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }
    }
  }

  "ManagementRoutes on POST /api-keys" when {

    val uri = Uri.unsafeFromString("/api-keys")
    val requestBody = CreateApiKeyRequest(
      name = name,
      description = description,
      ttl = ttlMinutes,
      scopes = List(scopeRead_1, scopeWrite_1)
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteApiKey))

    "JwtAuthorizer returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          AuthTestData.jwtWithMockedSignature.asRight
        )
        jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

        for {
          _ <- managementRoutes.run(request)
          _ = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
        } yield ()
      }

      "JwtOps returns Left containing ErrorInfo" should {

        val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Unauthorized
            _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps throws an exception" should {

        "return Internal Server Error" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps returns Right containing user ID, but request body is incorrect" when {

        "request body is provided with empty name" should {

          val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = ""))
          val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
            Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
          )

          "return Bad Request" in authorizedFixture {
            for {
              response <- managementRoutes.run(requestWithOnlyWhiteCharacters)
              _ = response.status shouldBe Status.BadRequest
              _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
            } yield ()
          }

          "NOT call ManagementService" in authorizedFixture {
            for {
              _ <- managementRoutes.run(requestWithOnlyWhiteCharacters)
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
              response <- managementRoutes.run(requestWithOnlyWhiteCharacters)
              _ = response.status shouldBe Status.BadRequest
              _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
            } yield ()
          }

          "NOT call ManagementService" in authorizedFixture {
            for {
              _ <- managementRoutes.run(requestWithOnlyWhiteCharacters)
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
              response <- managementRoutes.run(requestWithLongName)
              _ = response.status shouldBe Status.BadRequest
              _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
            } yield ()
          }

          "NOT call ManagementService" in authorizedFixture {
            for {
              _ <- managementRoutes.run(requestWithLongName)
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
              response <- managementRoutes.run(requestWithOnlyWhiteCharacters)
              _ = response.status shouldBe Status.BadRequest
              _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
            } yield ()
          }

          "NOT call ManagementService" in authorizedFixture {
            for {
              _ <- managementRoutes.run(requestWithOnlyWhiteCharacters)
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
                response <- managementRoutes.run(requestWithLongName)
                _ = response.status shouldBe Status.BadRequest
                _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
              } yield ()
            }

            "NOT call ManagementService" in authorizedFixture {
              for {
                _ <- managementRoutes.run(requestWithLongName)
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
                response <- managementRoutes.run(requestWithNegativeTtl)
                _ = response.status shouldBe Status.BadRequest
                _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
              } yield ()
            }

            "NOT call ManagementService" in authorizedFixture {
              for {
                _ <- managementRoutes.run(requestWithNegativeTtl)
                _ = verifyZeroInteractions(managementService)
              } yield ()
            }
          }
        }
      }

      "JwtOps returns Right containing user ID and request body is correct" should {

        "call ManagementService" in authorizedFixture {
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
            (apiKey_1, apiKeyData_1).asRight
          )
          val expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).createApiKey(eqTo(expectedUserId), eqTo(requestBody))
          } yield ()
        }

        "return successful value returned by ManagementService" when {

          "provided with description" in authorizedFixture {
            managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
              (apiKey_1, apiKeyData_1).asRight
            )

            for {
              response <- managementRoutes.run(request)
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
              response <- managementRoutes.run(requestWithoutDescription)
              _ = response.status shouldBe Status.Created
              _ <- response
                .as[CreateApiKeyResponse]
                .asserting(_ shouldBe CreateApiKeyResponse(apiKey_1.value, apiKeyDataWithoutDescription))
            } yield ()
          }
        }

        "return Bad Request when ManagementService returns successful IO with Left containing ValidationError" in authorizedFixture {
          val error = ValidationError(Seq(NotAllowedScopesProvidedError(Set("illegal-scope-1", "illegal-scope-2"))))
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(Left(error))

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(error.message)))
          } yield ()
        }

        "return Internal Server Error when ManagementService returns successful IO with Left containing InsertionError" in authorizedFixture {
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
            Left(InsertionError(ApiKeyIdAlreadyExistsError))
          )

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }

        "return Internal Server Error when ManagementService returns failed IO" in authorizedFixture {
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.raiseError(testException)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }
      }
    }
  }

  "ManagementRoutes on GET /api-keys" when {

    val uri = Uri.unsafeFromString("/api-keys")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadApiKey))

    "JwtAuthorizer returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          AuthTestData.jwtWithMockedSignature.asRight
        )
        jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

        for {
          _ <- managementRoutes.run(request)
          _ = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
        } yield ()
      }

      "JwtOps returns Left containing ErrorInfo" should {

        val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Unauthorized
            _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps throws an exception" should {

        "return Internal Server Error" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps returns Right containing user ID" should {

        "call ManagementService" in authorizedFixture {
          managementService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)
          val expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).getAllApiKeysFor(eqTo(expectedUserId))
          } yield ()
        }

        "return successful value returned by ManagementService" when {

          "ManagementService returns empty List" in authorizedFixture {
            managementService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

            for {
              response <- managementRoutes.run(request)
              _ = response.status shouldBe Status.Ok
              _ <- response
                .as[GetMultipleApiKeysResponse]
                .asserting(_ shouldBe GetMultipleApiKeysResponse(List.empty))
            } yield ()
          }

          "ManagementService returns a List with several elements" in authorizedFixture {
            managementService.getAllApiKeysFor(any[String]) returns IO.pure(
              List(apiKeyData_1, apiKeyData_2, apiKeyData_3)
            )

            for {
              response <- managementRoutes.run(request)
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
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }
      }
    }
  }

  "ManagementRoutes on GET /api-keys/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/api-keys/$publicKeyId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.ReadApiKey))

    "provided with publicKeyId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/api-keys/this-is-not-a-valid-uuid")
        val requestWithIncorrectPublicKeyId = Request[IO](method = Method.GET, uri = uri)

        for {
          response <- managementRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          AuthTestData.jwtWithMockedSignature.asRight
        )
        jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

        for {
          _ <- managementRoutes.run(request)
          _ = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
        } yield ()
      }

      "JwtOps returns Left containing ErrorInfo" should {

        val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Unauthorized
            _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps throws an exception" should {

        "return Internal Server Error" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps returns Right containing user ID" should {

        "call ManagementService" in authorizedFixture {
          managementService.getApiKey(any[String], any[UUID]) returns IO.pure(Some(apiKeyData_1))
          val expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).getApiKey(eqTo(expectedUserId), eqTo(publicKeyId_1))
          } yield ()
        }

        "return successful value returned by ManagementService" in authorizedFixture {
          managementService.getApiKey(any[String], any[UUID]) returns IO.pure(Some(apiKeyData_1))

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[GetSingleApiKeyResponse].asserting(_ shouldBe GetSingleApiKeyResponse(apiKeyData_1))
          } yield ()
        }

        "return Not Found when ManagementService returns empty Option" in authorizedFixture {
          managementService.getApiKey(any[String], any[UUID]) returns IO.pure(None)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.NotFound
            _ <- response
              .as[ErrorInfo]
              .asserting(
                _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.GetSingleApiKeyNotFound))
              )
          } yield ()
        }

        "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
          managementService.getApiKey(any[String], any[UUID]) returns IO.raiseError(testException)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }
      }
    }
  }

  "ManagementRoutes on DELETE /api-keys/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/api-keys/$publicKeyId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request)(Set(JwtPermissions.WriteApiKey))

    "provided with publicKeyId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/api-keys/this-is-not-a-valid-uuid")
        val requestWithIncorrectPublicKeyId = Request[IO](method = Method.DELETE, uri = uri)

        for {
          response <- managementRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          AuthTestData.jwtWithMockedSignature.asRight
        )
        jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

        for {
          _ <- managementRoutes.run(request)
          _ = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
        } yield ()
      }

      "JwtOps returns Left containing ErrorInfo" should {

        val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Unauthorized
            _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            jwtWithEmptySubField.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) returns Left(jwtOpsTestError)

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps throws an exception" should {

        "return Internal Server Error" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtAuthorizer.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            _ <- managementRoutes.run(request)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "JwtOps returns Right containing user ID" should {

        "call ManagementService" in authorizedFixture {
          managementService.deleteApiKeyBelongingToUserWith(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))
          val expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).deleteApiKeyBelongingToUserWith(eqTo(expectedUserId), eqTo(publicKeyId_1))
          } yield ()
        }

        "return Ok and ApiKeyData returned by ManagementService" in authorizedFixture {
          managementService.deleteApiKeyBelongingToUserWith(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[DeleteApiKeyResponse].asserting(_ shouldBe DeleteApiKeyResponse(apiKeyData_1))
          } yield ()
        }

        "return Not Found when ManagementService returns Left containing ApiKeyDataNotFound" in authorizedFixture {
          managementService.deleteApiKeyBelongingToUserWith(any[String], any[UUID]) returns IO.pure(
            Left(ApiKeyDataNotFoundError(userId_1, publicKeyId_1))
          )

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.NotFound
            _ <- response
              .as[ErrorInfo]
              .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.DeleteApiKeyNotFound)))
          } yield ()
        }

        "return Internal Server Error when ManagementService returns Left containing ApiKeyNotFoundError" in authorizedFixture {
          managementService.deleteApiKeyBelongingToUserWith(any[String], any[UUID]) returns IO.pure(
            Left(ApiKeyNotFoundError)
          )

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response
              .as[ErrorInfo]
              .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }

        "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
          managementService.deleteApiKeyBelongingToUserWith(any[String], any[UUID]) returns IO.raiseError(testException)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }
      }
    }
  }
}
