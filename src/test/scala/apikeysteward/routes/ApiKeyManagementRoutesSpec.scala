package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenant_1}
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError.ApiKeyIdAlreadyExistsError
import apikeysteward.model.errors.ApiKeyDbError.{ApiKeyDataNotFoundError, ApiKeyNotFoundError}
import apikeysteward.model.errors.CommonError.UserDoesNotExistError
import apikeysteward.routes.auth.JwtAuthorizer.{AccessToken, Permission}
import apikeysteward.routes.auth.model.{JsonWebToken, JwtPermissions}
import apikeysteward.routes.auth.{AuthTestData, JwtAuthorizer, JwtOps}
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.TtlTooLargeError
import cats.data.EitherT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.{HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import scala.concurrent.duration.Duration

class ApiKeyManagementRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with EitherValues
    with RoutesSpecBase {

  private val jwtOps               = mock[JwtOps]
  private val jwtAuthorizer        = mock[JwtAuthorizer]
  private val activeTenantVerifier = mock[ActiveTenantVerifier]
  private val managementService    = mock[ApiKeyManagementService]

  private val managementRoutes: HttpApp[IO] =
    new ApiKeyManagementRoutes(jwtOps, jwtAuthorizer, activeTenantVerifier, managementService).allRoutes.orNotFound

  private val jwtOpsTestError = ErrorInfo.unauthorizedErrorInfo(Some("JwtOps encountered an error."))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtOps, jwtAuthorizer, activeTenantVerifier, managementService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] = {
    activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
    jwtOps.extractUserId(any[JsonWebToken]).returns(AuthTestData.jwtWithMockedSignature.claim.subject.get.asRight)
    authorizedFixture(jwtAuthorizer)(test)
  }

  private def runCommonJwtTests(request: Request[IO])(requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(managementRoutes, jwtAuthorizer, List(managementService))(request, requiredPermissions)

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    runCommonTenantIdHeaderTests(managementRoutes, jwtAuthorizer, List(managementService))(request)

  "ManagementRoutes on POST /api-keys" when {

    val uri = Uri.unsafeFromString("/api-keys")
    val requestBody = CreateApiKeyRequest(
      name = name_1,
      description = description_1,
      ttl = ttl,
      templateId = publicTemplateId_1,
      permissionIds = List(
        publicPermissionId_1,
        publicPermissionId_2,
        publicPermissionId_3
      )
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteApiKey))

    runCommonTenantIdHeaderTests(request)

    "everything works correctly" should {

      "call ActiveTenantVerifier, JwtOps and ManagementService" in authorizedFixture {
        managementService
          .createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest])
          .returns(IO.pure((apiKey_1, apiKeyData_1).asRight))

        for {
          _ <- managementRoutes.run(request)

          _              = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
          _              = verify(activeTenantVerifier).verifyTenantIsActive(eqTo(publicTenantId_1))
          expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get
          _ = verify(managementService).createApiKey(eqTo(publicTenantId_1), eqTo(expectedUserId), eqTo(requestBody))
        } yield ()
      }

      "return Ok and successful value returned by ManagementService" in authorizedFixture {
        managementService
          .createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest])
          .returns(IO.pure((apiKey_1, apiKeyData_1).asRight))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ <- response
            .as[CreateApiKeyResponse]
            .asserting(_ shouldBe CreateApiKeyResponse(apiKey_1.value, apiKeyData_1))
        } yield ()
      }

      "return Bad Request when ManagementService returns successful IO with Left containing ValidationError" in authorizedFixture {
        val error = ValidationError(TtlTooLargeError(requestBody.ttl, ttl.minus(Duration(1, ttl.unit))))
        managementService
          .createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest])
          .returns(IO.pure(Left(error)))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(error.message)))
        } yield ()
      }

      "return Internal Server Error when ManagementService returns successful IO with Left containing InsertionError" in authorizedFixture {
        managementService
          .createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest])
          .returns(IO.pure(Left(InsertionError(ApiKeyIdAlreadyExistsError))))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ManagementService returns failed IO" in authorizedFixture {
        managementService
          .createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest])
          .returns(IO.raiseError(testException))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }

    "ActiveTenantVerifier returns Left containing error" should {

      "NOT call JwtOps or ManagementService" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }

      "return BadRequest" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated)))
        } yield ()
      }
    }

    "JwtOps returns Left containing ErrorInfo" should {

      val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
        claim = AuthTestData.jwtClaim.copy(subject = None)
      )

      "return Unauthorized" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(IO.pure(jwtWithEmptySubField.asRight))
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
        } yield ()
      }

      "NOT call ManagementService" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(IO.pure(jwtWithEmptySubField.asRight))
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtOps throws an exception" should {

      "return Internal Server Error" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "request body is incorrect" when {

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

        val nameThatIsTooLong   = List.fill(251)("A").mkString
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

        val requestWithNegativeTtl = request.withEntity(requestBody.copy(ttl = Duration(-1, ttl.unit)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected ttl to pass validation, but got: -1 minutes)")
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

      "request body is provided with templateId which is NOT a UUID" should {

        val requestBodyWithIncorrectTemplateId =
          s"""{
             |  "name": "$name_1",
             |  "description": "$description_1",
             |  "ttl": "$ttl",
             |  "templateId": "this-is-not-a-uuid",
             |  "permissionIds": [
             |    "$publicPermissionId_1",
             |    "$publicPermissionId_2",
             |    "$publicPermissionId_3"
             |  ]
             |}""".stripMargin

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (Got value '\"this-is-not-a-uuid\"' with wrong type, expecting string at 'templateId')"
          )
        )

        "return Bad Request" in authorizedFixture {
          val requestWithIncorrectTemplateId =
            request.withEntity(parser.parse(requestBodyWithIncorrectTemplateId).value)

          for {
            response <- managementRoutes.run(requestWithIncorrectTemplateId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          val requestWithIncorrectTemplateId =
            request.withEntity(parser.parse(requestBodyWithIncorrectTemplateId).value)

          for {
            _ <- managementRoutes.run(requestWithIncorrectTemplateId)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with permissionIds containing ID which is NOT a UUID" should {

        val requestBodyWithIncorrectPermissionId =
          s"""{
             |  "name": "$name_1",
             |  "description": "$description_1",
             |  "ttl": "$ttl",
             |  "templateId": "$publicTemplateId_1",
             |  "permissionIds": [
             |    "$publicPermissionId_1",
             |    "this-is-not-a-uuid",
             |    "$publicPermissionId_3"
             |  ]
             |}""".stripMargin

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (Got value '\"this-is-not-a-uuid\"' with wrong type, expecting string at 'permissionIds[1]')"
          )
        )

        "return Bad Request" in authorizedFixture {
          val requestWithIncorrectPermissionId =
            request.withEntity(parser.parse(requestBodyWithIncorrectPermissionId).value)

          for {
            response <- managementRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          val requestWithIncorrectPermissionId =
            request.withEntity(parser.parse(requestBodyWithIncorrectPermissionId).value)

          for {
            _ <- managementRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }
    }
  }

  "ManagementRoutes on GET /api-keys" when {

    val uri     = Uri.unsafeFromString("/api-keys")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request)(Set(JwtPermissions.ReadApiKey))

    runCommonTenantIdHeaderTests(request)

    "everything works correctly" should {

      "call ActiveTenantVerifier, JwtOps and ManagementService" in authorizedFixture {
        managementService.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(Right(List.empty)))

        for {
          _ <- managementRoutes.run(request)

          _              = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
          _              = verify(activeTenantVerifier).verifyTenantIsActive(eqTo(publicTenantId_1))
          expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get
          _              = verify(managementService).getAllForUser(eqTo(publicTenantId_1), eqTo(expectedUserId))
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "ManagementService returns empty List" in authorizedFixture {
          managementService.getAllForUser(any[TenantId], any[UserId]).returns(IO.pure(Right(List.empty)))

          for {
            response <- managementRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeysResponse]
              .asserting(_ shouldBe GetMultipleApiKeysResponse(List.empty))
          } yield ()
        }

        "ManagementService returns a List with several elements" in authorizedFixture {
          managementService
            .getAllForUser(any[TenantId], any[UserId])
            .returns(
              IO.pure(
                Right(List(apiKeyData_1, apiKeyData_2, apiKeyData_3))
              )
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

      "return Bad Request when ManagementService returns ReferencedUserDoesNotExistError" in authorizedFixture {
        managementService
          .getAllForUser(any[TenantId], any[UserId])
          .returns(
            IO.pure(
              Left(UserDoesNotExistError(publicTenantId_1, publicUserId_1))
            )
          )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.UserNotFound)))
        } yield ()
      }

      "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
        managementService.getAllForUser(any[TenantId], any[UserId]).returns(IO.raiseError(testException))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }

    "ActiveTenantVerifier returns Left containing error" should {

      "NOT call JwtOps or ManagementService" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }

      "return BadRequest" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated)))
        } yield ()
      }
    }

    "JwtOps returns Left containing ErrorInfo" should {

      val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
        claim = AuthTestData.jwtClaim.copy(subject = None)
      )

      "return Unauthorized" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(IO.pure(jwtWithEmptySubField.asRight))
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              jwtWithEmptySubField.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtOps throws an exception" should {

      "return Internal Server Error" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }
  }

  "ManagementRoutes on GET /api-keys/{publicKeyId}" when {

    val uri     = Uri.unsafeFromString(s"/api-keys/$publicKeyId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request)(Set(JwtPermissions.ReadApiKey))

    runCommonTenantIdHeaderTests(request)

    "provided with publicKeyId which is not an UUID" should {

      val uri                             = Uri.unsafeFromString("/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId = request.withUri(uri)

      "return Bad Request" in {
        for {
          response <- managementRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }

      "NOT call JwtOps or ManagementService" in authorizedFixture {
        for {
          _ <- managementRoutes.run(requestWithIncorrectPublicKeyId)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }
    }

    "everything works correctly" should {

      "call ActiveTenantVerifier, JwtOps and ManagementService" in authorizedFixture {
        managementService.getApiKey(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.pure(Some(apiKeyData_1)))

        for {
          _ <- managementRoutes.run(request)

          _              = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
          _              = verify(activeTenantVerifier).verifyTenantIsActive(eqTo(publicTenantId_1))
          expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get
          _ = verify(managementService).getApiKey(eqTo(publicTenantId_1), eqTo(expectedUserId), eqTo(publicKeyId_1))
        } yield ()
      }

      "return successful value returned by ManagementService" in authorizedFixture {
        managementService.getApiKey(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.pure(Some(apiKeyData_1)))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[GetSingleApiKeyResponse].asserting(_ shouldBe GetSingleApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when ManagementService returns empty Option" in authorizedFixture {
        managementService.getApiKey(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.pure(None))

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
        managementService.getApiKey(any[TenantId], any[UserId], any[ApiKeyId]).returns(IO.raiseError(testException))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }

    "ActiveTenantVerifier returns Left containing error" should {

      "NOT call JwtOps or ManagementService" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }

      "return BadRequest" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated)))
        } yield ()
      }
    }

    "JwtOps returns Left containing ErrorInfo" should {

      val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
        claim = AuthTestData.jwtClaim.copy(subject = None)
      )

      "return Unauthorized" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(IO.pure(jwtWithEmptySubField.asRight))
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              jwtWithEmptySubField.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtOps throws an exception" should {

      "return Internal Server Error" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }
  }

  "ManagementRoutes on DELETE /api-keys/{publicKeyId}" when {

    val uri     = Uri.unsafeFromString(s"/api-keys/$publicKeyId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = allHeaders)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteApiKey))

    runCommonTenantIdHeaderTests(request)

    "provided with publicKeyId which is not an UUID" should {

      val uri                             = Uri.unsafeFromString("/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId = request.withUri(uri)

      "return Bad Request" in {
        for {
          response <- managementRoutes.run(requestWithIncorrectPublicKeyId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter keyId")))
        } yield ()
      }

      "NOT call JwtOps or ManagementService" in authorizedFixture {
        for {
          _ <- managementRoutes.run(requestWithIncorrectPublicKeyId)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }
    }

    "everything works correctly" should {

      "call ActiveTenantVerifier, JwtOps and ManagementService" in authorizedFixture {
        managementService
          .deleteApiKeyBelongingToUserWith(any[TenantId], any[UserId], any[ApiKeyId])
          .returns(IO.pure(Right(apiKeyData_1)))

        for {
          _ <- managementRoutes.run(request)

          _              = verify(jwtOps).extractUserId(eqTo(AuthTestData.jwtWithMockedSignature))
          _              = verify(activeTenantVerifier).verifyTenantIsActive(eqTo(publicTenantId_1))
          expectedUserId = AuthTestData.jwtWithMockedSignature.claim.subject.get
          _ = verify(managementService)
            .deleteApiKeyBelongingToUserWith(eqTo(publicTenantId_1), eqTo(expectedUserId), eqTo(publicKeyId_1))
        } yield ()
      }

      "return Ok and ApiKeyData returned by ManagementService" in authorizedFixture {
        managementService
          .deleteApiKeyBelongingToUserWith(any[TenantId], any[UserId], any[ApiKeyId])
          .returns(IO.pure(Right(apiKeyData_1)))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[DeleteApiKeyResponse].asserting(_ shouldBe DeleteApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when ManagementService returns Left containing ApiKeyDataNotFound" in authorizedFixture {
        managementService
          .deleteApiKeyBelongingToUserWith(any[TenantId], any[UserId], any[ApiKeyId])
          .returns(
            IO.pure(
              Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1))
            )
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
        managementService
          .deleteApiKeyBelongingToUserWith(any[TenantId], any[UserId], any[ApiKeyId])
          .returns(
            IO.pure(
              Left(ApiKeyNotFoundError)
            )
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
        managementService
          .deleteApiKeyBelongingToUserWith(any[TenantId], any[UserId], any[ApiKeyId])
          .returns(
            IO
              .raiseError(testException)
          )

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }

    "ActiveTenantVerifier returns Left containing error" should {

      "NOT call JwtOps or ManagementService" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(jwtOps, managementService)
        } yield ()
      }

      "return BadRequest" in authorizedFixture {
        activeTenantVerifier
          .verifyTenantIsActive(any[TenantId])
          .returns(EitherT.leftT(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated)))
        } yield ()
      }
    }

    "JwtOps returns Left containing ErrorInfo" should {

      val jwtWithEmptySubField = AuthTestData.jwtWithMockedSignature.copy(
        claim = AuthTestData.jwtClaim.copy(subject = None)
      )

      "return Unauthorized" in {
        activeTenantVerifier.verifyTenantIsActive(any[TenantId]).returns(EitherT.rightT(tenant_1))
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(IO.pure(jwtWithEmptySubField.asRight))
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.Unauthorized
          _ <- response.as[ErrorInfo].asserting(_ shouldBe jwtOpsTestError)
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              jwtWithEmptySubField.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).returns(Left(jwtOpsTestError))

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }

    "JwtOps throws an exception" should {

      "return Internal Server Error" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          response <- managementRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
        } yield ()
      }

      "NOT call ManagementService" in {
        jwtAuthorizer
          .authorisedWithPermissions(any[Set[Permission]])(any[AccessToken])
          .returns(
            IO.pure(
              AuthTestData.jwtWithMockedSignature.asRight
            )
          )
        jwtOps.extractUserId(any[JsonWebToken]).throws(testException)

        for {
          _ <- managementRoutes.run(request)
          _ = verifyZeroInteractions(managementService)
        } yield ()
      }
    }
  }

}
