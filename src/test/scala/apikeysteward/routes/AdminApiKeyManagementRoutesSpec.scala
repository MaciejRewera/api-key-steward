package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError.ApiKeyIdAlreadyExistsError
import apikeysteward.model.errors.ApiKeyDbError.{ApiKeyDataNotFoundError, ApiKeyNotFoundError}
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.apikey._
import apikeysteward.routes.model.apikey._
import apikeysteward.services.ApiKeyManagementService
import apikeysteward.services.ApiKeyManagementService.ApiKeyCreateError.{InsertionError, ValidationError}
import apikeysteward.services.CreateApiKeyRequestValidator.CreateApiKeyRequestValidatorError.TtlTooLargeError
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

class AdminApiKeyManagementRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with EitherValues
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
    runCommonJwtTests(adminRoutes, jwtAuthorizer, List(managementService))(request, requiredPermissions)

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    runCommonTenantIdHeaderTests(adminRoutes, jwtAuthorizer, List(managementService))(request)

  "AdminApiKeyRoutes on POST /admin/api-keys" when {

    val uri = Uri.unsafeFromString(s"/admin/api-keys")
    val requestBody = CreateApiKeyAdminRequest(
      userId = publicUserId_1,
      name = name_1,
      description = description_1,
      templateId = publicTemplateId_1,
      ttl = ttl,
      permissionIds = List(
        publicPermissionId_1,
        publicPermissionId_2,
        publicPermissionId_3
      )
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

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

      "request body is provided with userId longer than 255 characters" should {

        val userIdThatIsTooLong = List.fill(256)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(userId = userIdThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected userId to have length less than or equal to 255, but got: "$userIdThatIsTooLong")"""
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

        val requestWithNegativeTtl = request.withEntity(requestBody.copy(ttl = Duration(-1, ttl.unit)))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected ttl to pass validation, but got: -1 minutes)")
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

      "request body is provided with templateId which is NOT a UUID" should {

        val requestBodyWithIncorrectTemplateId =
          s"""{
             |  "userId": "$publicUserId_1",
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
            response <- adminRoutes.run(requestWithIncorrectTemplateId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          val requestWithIncorrectTemplateId =
            request.withEntity(parser.parse(requestBodyWithIncorrectTemplateId).value)

          for {
            _ <- adminRoutes.run(requestWithIncorrectTemplateId)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }

      "request body is provided with permissionIds containing ID which is NOT a UUID" should {

        val requestBodyWithIncorrectPermissionId =
          s"""{
             |  "userId": "$publicUserId_1",
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
            response <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ManagementService" in authorizedFixture {
          val requestWithIncorrectPermissionId =
            request.withEntity(parser.parse(requestBodyWithIncorrectPermissionId).value)

          for {
            _ <- adminRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(managementService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ManagementService" in authorizedFixture {
        managementService.createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest]) returns IO.pure(
          (apiKey_1, apiKeyData_1).asRight
        )

        for {
          _ <- adminRoutes.run(request)

          expectedRequest = CreateApiKeyRequest(
            name = requestBody.name,
            description = requestBody.description,
            templateId = requestBody.templateId,
            ttl = requestBody.ttl,
            permissionIds = requestBody.permissionIds
          )
          _ = verify(managementService).createApiKey(
            eqTo(publicTenantId_1),
            eqTo(publicUserId_1),
            eqTo(expectedRequest)
          )
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "provided with description" in authorizedFixture {
          managementService.createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest]) returns IO.pure(
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
          managementService.createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest]) returns IO.pure(
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
        val error = ValidationError(Seq(TtlTooLargeError(requestBody.ttl, ttl.minus(Duration(1, ttl.unit)))))
        managementService.createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest]) returns IO.pure(
          Left(error)
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.badRequestErrorInfo(Some(error.message)))
        } yield ()
      }

      "return Internal Server Error when ManagementService returns successful IO with Left containing InsertionError" in authorizedFixture {
        managementService.createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest]) returns IO.pure(
          Left(InsertionError(ApiKeyIdAlreadyExistsError))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ManagementService returns failed IO" in authorizedFixture {
        managementService.createApiKey(any[TenantId], any[UserId], any[CreateApiKeyRequest]) returns IO.raiseError(
          testException
        )

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
    val requestBody = UpdateApiKeyAdminRequest(name = name_1, description = description_1)

    val request = Request[IO](method = Method.PUT, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "provided with publicKeyId which is not an UUID" should {

      val uri = Uri.unsafeFromString(s"/admin/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId = request.withUri(uri)

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
        managementService.updateApiKey(any[TenantId], any[ApiKeyId], any[UpdateApiKeyAdminRequest]) returns IO.pure(
          apiKeyData_1.asRight
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).updateApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "provided with description" in authorizedFixture {
          managementService.updateApiKey(any[TenantId], any[ApiKeyId], any[UpdateApiKeyAdminRequest]) returns IO.pure(
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
          managementService.updateApiKey(any[TenantId], any[ApiKeyId], any[UpdateApiKeyAdminRequest]) returns IO.pure(
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
        val error = ApiKeyDbError.ApiKeyDataNotFoundError(publicUserId_1, publicKeyIdStr_1)
        managementService.updateApiKey(any[TenantId], any[ApiKeyId], any[UpdateApiKeyAdminRequest]) returns IO.pure(
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
        managementService.updateApiKey(any[TenantId], any[ApiKeyId], any[UpdateApiKeyAdminRequest]) returns IO
          .raiseError(testException)

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
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request)(Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "provided with publicKeyId which is not an UUID" should {

      val uri = Uri.unsafeFromString(s"/admin/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId = request.withUri(uri)

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
        managementService.getApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(Some(apiKeyData_1))

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).getApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
        } yield ()
      }

      "return successful value returned by ManagementService" in authorizedFixture {
        managementService.getApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(Some(apiKeyData_1))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[GetSingleApiKeyResponse].asserting(_ shouldBe GetSingleApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when ManagementService returns empty Option" in authorizedFixture {
        managementService.getApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(None)

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
        managementService.getApiKey(any[TenantId], any[ApiKeyId]) returns IO.raiseError(testException)

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
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = allHeaders)

    runCommonJwtTests(request)(Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "provided with publicKeyId which is not an UUID" should {

      val uri = Uri.unsafeFromString(s"/admin/api-keys/this-is-not-a-valid-uuid")
      val requestWithIncorrectPublicKeyId = request.withUri(uri)

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
        managementService.deleteApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(Right(apiKeyData_1))

        for {
          _ <- adminRoutes.run(request)
          _ = verify(managementService).deleteApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
        } yield ()
      }

      "return Ok and ApiKeyData returned by ManagementService" in authorizedFixture {
        managementService.deleteApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(Right(apiKeyData_1))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[DeleteApiKeyResponse].asserting(_ shouldBe DeleteApiKeyResponse(apiKeyData_1))
        } yield ()
      }

      "return Not Found when ManagementService returns Left containing ApiKeyDataNotFoundError" in authorizedFixture {
        managementService.deleteApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(
          Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1))
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
        managementService.deleteApiKey(any[TenantId], any[ApiKeyId]) returns IO.pure(Left(ApiKeyNotFoundError))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response
            .as[ErrorInfo]
            .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ManagementService returns an exception" in authorizedFixture {
        managementService.deleteApiKey(any[TenantId], any[ApiKeyId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }
}
