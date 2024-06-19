package apikeysteward.routes

import apikeysteward.base.TestData._
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.{ApiKeyDataNotFound, GenericApiKeyDeletionError}
import apikeysteward.routes.auth.JwtValidator.{AccessToken, Permission}
import apikeysteward.routes.auth.model.{JsonWebToken, JwtPermissions}
import apikeysteward.routes.auth.{AuthTestData, JwtOps, JwtValidator}
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.{CreateApiKeyRequest, CreateApiKeyResponse, DeleteApiKeyResponse}
import apikeysteward.services.ManagementService
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

class ManagementRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val jwtOps = mock[JwtOps]
  private val jwtValidator = mock[JwtValidator]
  private val managementService = mock[ManagementService]

  private val managementRoutes: HttpApp[IO] =
    new ManagementRoutes(jwtOps, jwtValidator, managementService).allRoutes.orNotFound

  private val tokenString: AccessToken = "TOKEN"
  private val authorizationHeader: Authorization = Authorization(Credentials.Token(Bearer, tokenString))

  private val testException = new RuntimeException("Test Exception")
  private val jwtOpsTestError = ErrorInfo.unauthorizedErrorInfo(Some("JwtOps encountered an error."))

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwtOps, jwtValidator, managementService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] = IO {
    jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
      AuthTestData.jwtWithMockedSignature.asRight
    )
    jwtOps.extractUserId(any[JsonWebToken]) returns AuthTestData.jwtWithMockedSignature.jwtClaim.subject.get.asRight
  }.flatMap(_ => test)

  "ManagementRoutes on POST /api-key" when {

    val uri = Uri.unsafeFromString("api-key")
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
          response <- managementRoutes.run(requestWithoutJwt)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.unauthorizedErrorInfo(Some("Invalid value for: header Authorization (missing)"))
            )
        } yield ()
      }

      "NOT call either JwtValidator or ManagementService" in {
        for {
          _ <- managementRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, managementService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- managementRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.WriteApiKey)))(eqTo(tokenString))
      } yield ()
    }

    "JwtValidator returns Left containing error" should {

      val jwtValidatorError = ErrorInfo.unauthorizedErrorInfo(Some("A message explaining why auth validation failed."))

      "return Unauthorized" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtClaim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(apiKey_1, apiKeyData_1)
          val expectedUserId = AuthTestData.jwtWithMockedSignature.jwtClaim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).createApiKey(eqTo(expectedUserId), eqTo(requestBody))
          } yield ()
        }

        "return the value returned by ManagementService" when {

          "provided with description" in authorizedFixture {
            managementService.createApiKey(any[String], any[CreateApiKeyRequest]) returns IO.pure(
              apiKey_1,
              apiKeyData_1
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
              apiKey_1,
              apiKeyDataWithoutDescription
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

  "ManagementRoutes on GET /api-key" when {

    val uri = Uri.unsafeFromString("api-key")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

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

      "NOT call either JwtValidator or ManagementService" in {
        for {
          _ <- managementRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, managementService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- managementRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.ReadApiKey)))(eqTo(tokenString))
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
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtClaim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          val expectedUserId = AuthTestData.jwtWithMockedSignature.jwtClaim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).getAllApiKeysFor(eqTo(expectedUserId))
          } yield ()
        }

        "return Ok and an empty List when ManagementService returns empty List" in authorizedFixture {
          managementService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[List[ApiKeyData]].asserting(_ shouldBe List.empty[ApiKeyData])
          } yield ()
        }

        "return Ok and all ApiKeyData when ManagementService returns non-empty List" in authorizedFixture {
          managementService.getAllApiKeysFor(any[String]) returns IO.pure(
            List(apiKeyData_1, apiKeyData_2, apiKeyData_3)
          )

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[List[ApiKeyData]].asserting(_ shouldBe List(apiKeyData_1, apiKeyData_2, apiKeyData_3))
          } yield ()
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

  "ManagementRoutes on DELETE /api-key/{publicKeyId}" when {

    val uri = Uri.unsafeFromString(s"/api-key/$publicKeyId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

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

      "NOT call either JwtValidator or ManagementService" in {
        for {
          _ <- managementRoutes.run(requestWithoutJwt)
          _ = verifyZeroInteractions(jwtValidator, managementService)
        } yield ()
      }
    }

    "the JWT is provided should call JwtValidator providing access token" in {
      jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
        ErrorInfo.unauthorizedErrorInfo().asLeft
      )

      for {
        _ <- managementRoutes.run(request)
        _ = verify(jwtValidator).authorisedWithPermissions(eqTo(Set(JwtPermissions.WriteApiKey)))(eqTo(tokenString))
      } yield ()
    }

    "JwtValidator returns failed IO" should {

      "return Internal Server Error" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.raiseError(
          testException
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
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
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtValidatorError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
          jwtValidatorError.asLeft
        )

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtValidator returns Right containing JsonWebToken" when {

      "should always call JwtOps" in {
        jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtClaim = AuthTestData.jwtClaim.copy(subject = None)
        )

        "return Unauthorized" in {
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
            AuthTestData.jwtWithMockedSignature.asRight
          )
          jwtOps.extractUserId(any[JsonWebToken]) throws testException

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
          } yield ()
        }

        "NOT call ManagementService" in {
          jwtValidator.authorisedWithPermissions(any[Set[Permission]])(any[AccessToken]) returns IO.pure(
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
          managementService.deleteApiKey(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))
          val expectedUserId = AuthTestData.jwtWithMockedSignature.jwtClaim.subject.get

          for {
            _ <- managementRoutes.run(request)
            _ = verify(managementService).deleteApiKey(eqTo(expectedUserId), eqTo(publicKeyId_1))
          } yield ()
        }

        "return Ok and ApiKeyData returned by ManagementService" in authorizedFixture {
          managementService.deleteApiKey(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[DeleteApiKeyResponse].asserting(_ shouldBe DeleteApiKeyResponse(apiKeyData_1))
          } yield ()
        }

        "return Not Found when ManagementService returns Left containing ApiKeyDataNotFound" in authorizedFixture {
          managementService.deleteApiKey(any[String], any[UUID]) returns IO.pure(
            Left(ApiKeyDataNotFound(userId_1, publicKeyId_1))
          )

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.NotFound
            _ <- response
              .as[ErrorInfo]
              .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.Management.DeleteApiKeyNotFound)))
          } yield ()
        }

        "return Internal Server Error when ManagementService returns Left containing GenericApiKeyDeletionError" in authorizedFixture {
          managementService.deleteApiKey(any[String], any[UUID]) returns IO.pure(
            Left(GenericApiKeyDeletionError(userId_1, publicKeyId_1))
          )

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.InternalServerError
            _ <- response
              .as[ErrorInfo]
              .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
          } yield ()
        }

        "return Bad Request when provided with publicKeyId which is not an UUID" in authorizedFixture {
          val uri = Uri.unsafeFromString("/api-key/this-is-not-a-valid-uuid")
          val requestWithIncorrectPublicKeyId = Request[IO](method = Method.DELETE, uri = uri)

          for {
            response <- managementRoutes.run(requestWithIncorrectPublicKeyId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response
              .as[ErrorInfo]
              .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
          } yield ()
        }

        "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
          managementService.deleteApiKey(any[String], any[UUID]) returns IO.raiseError(testException)

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
