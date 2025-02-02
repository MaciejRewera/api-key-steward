package apikeysteward.routes

import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData.{
  apiKeyTemplatesUsersEntityWrite_1_1,
  apiKeyTemplatesUsersEntityWrite_1_2,
  apiKeyTemplatesUsersEntityWrite_1_3
}
import apikeysteward.base.testdata.ApiKeysTestData.{apiKeyData_1, apiKeyData_2, apiKeyData_3, publicKeyId_1}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantIdStr_1, publicTenantId_1, tenantDbId_1}
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError._
import apikeysteward.model.errors.GenericError.UserDoesNotExistError
import apikeysteward.model.errors.UserDbError.UserInsertionError._
import apikeysteward.model.errors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.repositories.UserRepository.UserRepositoryError
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.apikeytemplate.GetMultipleApiKeyTemplatesResponse
import apikeysteward.routes.model.admin.apikeytemplatesusers._
import apikeysteward.routes.model.admin.user._
import apikeysteward.routes.model.apikey.GetMultipleApiKeysResponse
import apikeysteward.services._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import fs2.Stream
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Header, Headers, HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.ci.{CIString, CIStringSyntax}

import java.sql.SQLException
import java.util.UUID

class AdminUserRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val userService = mock[UserService]
  private val apiKeyTemplateService = mock[ApiKeyTemplateService]
  private val apiKeyTemplateAssociationsService = mock[ApiKeyTemplateAssociationsService]
  private val apiKeyManagementService = mock[ApiKeyManagementService]

  private val adminUserRoutes: HttpApp[IO] =
    new AdminUserRoutes(
      jwtAuthorizer,
      userService,
      apiKeyTemplateService,
      apiKeyTemplateAssociationsService,
      apiKeyManagementService
    ).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, userService, apiKeyTemplateService, apiKeyTemplateAssociationsService, apiKeyManagementService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO], requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(
      adminUserRoutes,
      jwtAuthorizer,
      List(userService, apiKeyTemplateService, apiKeyTemplateAssociationsService, apiKeyManagementService)
    )(request, requiredPermissions)

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    runCommonTenantIdHeaderTests(
      adminUserRoutes,
      jwtAuthorizer,
      List(userService, apiKeyTemplateService, apiKeyTemplateAssociationsService, apiKeyManagementService)
    )(request)

  "AdminUserRoutes on POST /admin/users" when {

    val uri = Uri.unsafeFromString("/admin/users")
    val requestBody = CreateUserRequest(userId = publicUserId_1)

    val request = Request[IO](method = Method.POST, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty userId" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(userId = ""))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected userId to have length greater than or equal to 1, but got: "")""")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call UserService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(userService)
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
            response <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call UserService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(userService)
          } yield ()
        }
      }

      "request body is provided with userId longer than 255 characters" should {

        val nameThatIsTooLong = List.fill(256)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(userId = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected userId to have length less than or equal to 255, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call UserService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(userService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call UserService" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          user_1.asRight
        )

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).createUser(eqTo(publicTenantId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by UserService" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(user_1.asRight)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ <- response
            .as[CreateUserResponse]
            .asserting(_ shouldBe CreateUserResponse(user_1))
        } yield ()
      }

      "return Bad Request when UserService returns successful IO with Left containing UserAlreadyExistsForThisTenantError" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          Left(UserAlreadyExistsForThisTenantError(publicUserId_1, tenantDbId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.UserAlreadyExistsForThisTenant))
            )
        } yield ()
      }

      "return Bad Request when UserService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          Left(UserInsertionError.ReferencedTenantDoesNotExistError(publicTenantId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns successful IO with Left containing UserInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.pure(
          Left(UserInsertionErrorImpl(testSqlException))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.createUser(any[UUID], any[CreateUserRequest]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on DELETE /admin/users/{userId}" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(user_1.asRight)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).deleteUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return successful value returned by UserService" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(user_1.asRight)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeleteUserResponse]
            .asserting(_ shouldBe DeleteUserResponse(user_1))
        } yield ()
      }

      "return Not Found when UserService returns successful IO with Left containing UserNotFoundError" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(
          Left(UserRepositoryError(UserNotFoundError(publicTenantId_1, publicUserId_1)))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminUser.UserNotFound))
            )
        } yield ()
      }

      "return Not Found when UserService returns successful IO with Left containing ApiKeyDbError" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.pure(
          Left(UserRepositoryError(ApiKeyDataNotFoundError(publicKeyId_1)))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.deleteUser(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on GET /admin/users/{userId}" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.pure(user_1.some)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return successful value returned by UserService" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.pure(user_1.some)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSingleUserResponse]
            .asserting(_ shouldBe GetSingleUserResponse(user_1))
        } yield ()
      }

      "return Not Found when UserService returns empty Option" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.pure(none)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminUser.UserNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.getBy(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on GET /admin/users" when {

    val uri = uri"/admin/users"
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call UserService" in authorizedFixture {
        userService.getAllForTenant(any[TenantId]) returns IO.pure(Right(List.empty))

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(userService).getAllForTenant(eqTo(publicTenantId_1))
        } yield ()
      }

      "return the value returned by UserService" when {

        "UserService returns an empty List" in authorizedFixture {
          userService.getAllForTenant(any[TenantId]) returns IO.pure(Right(List.empty))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[GetMultipleUsersResponse].asserting(_ shouldBe GetMultipleUsersResponse(List.empty))
          } yield ()
        }

        "UserService returns a List with several elements" in authorizedFixture {
          userService.getAllForTenant(any[TenantId]) returns IO.pure(Right(List(user_1, user_2, user_3)))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleUsersResponse]
              .asserting(_ shouldBe GetMultipleUsersResponse(List(user_1, user_2, user_3)))
          } yield ()
        }
      }

      "return Bad Request when UserService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        userService.getAllForTenant(any[TenantId]) returns IO.pure(
          Left(UserInsertionError.ReferencedTenantDoesNotExistError(publicTenantId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.AdminUser.ReferencedTenantNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when UserService returns failed IO" in authorizedFixture {
        userService.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on POST /admin/users/{userId}/templates" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1/templates")
    val requestBody = AssociateApiKeyTemplatesWithUserRequest(templateIds =
      List(publicTemplateId_1, publicTemplateId_2, publicTemplateId_3)
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty List" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(templateIds = List.empty))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected size of templateIds to be greater than or equal to 1, but got 0)")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateAssociationsService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateAssociationsService)
          } yield ()
        }
      }

      "request body contains TemplateId which is an empty String" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "templateIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (Got value '\"\"' with wrong type, expecting string at 'templateIds[1]')")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateAssociationsService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateAssociationsService)
          } yield ()
        }
      }

      "request body contains TemplateId which is not an UUID" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "templateIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "this-is-not-a-valid-uuid",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (Got value '\"this-is-not-a-valid-uuid\"' with wrong type, expecting string at 'templateIds[1]')"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateAssociationsService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateAssociationsService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateAssociationsService" in authorizedFixture {
        apiKeyTemplateAssociationsService.associateApiKeyTemplatesWithUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(apiKeyTemplateAssociationsService).associateApiKeyTemplatesWithUser(
            eqTo(publicTenantId_1),
            eqTo(publicUserId_1),
            eqTo(requestBody.templateIds)
          )
        } yield ()
      }

      "return Created status and empty body" in authorizedFixture {
        apiKeyTemplateAssociationsService.associateApiKeyTemplatesWithUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ = response.body shouldBe Stream.empty
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateAssociationsService returns successful IO with Left containing ApiKeyTemplatesUsersAlreadyExistsError" in authorizedFixture {
        apiKeyTemplateAssociationsService.associateApiKeyTemplatesWithUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(Left(ApiKeyTemplatesUsersAlreadyExistsError(templateDbId_1, userDbId_1)))

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ApiKeyTemplatesUsersAlreadyExists)
              )
            )
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateAssociationsService returns successful IO with Left containing ReferencedApiKeyTemplateDoesNotExistError" in authorizedFixture {
        apiKeyTemplateAssociationsService.associateApiKeyTemplatesWithUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(
          Left(ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedApiKeyTemplateNotFound)
              )
            )
        } yield ()
      }

      "return Not Found when ApiKeyTemplateAssociationsService returns successful IO with Left containing ReferencedUserDoesNotExistError" in authorizedFixture {
        apiKeyTemplateAssociationsService.associateApiKeyTemplatesWithUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(Left(ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1)))

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateAssociationsService returns successful IO with Left containing ApiKeyTemplatesUsersInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        apiKeyTemplateAssociationsService.associateApiKeyTemplatesWithUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(Left(ApiKeyTemplatesUsersInsertionErrorImpl(testSqlException)))

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateAssociationsService returns failed IO" in authorizedFixture {
        apiKeyTemplateAssociationsService.associateUsersWithApiKeyTemplate(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on DELETE /admin/users/{userId}/templates" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1/templates")
    val requestBody = DeleteApiKeyTemplatesFromUserRequest(
      templateIds = List(publicTemplateId_1, publicTemplateId_2, publicTemplateId_3)
    )

    val request = Request[IO](method = Method.DELETE, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken, but request body is incorrect" when {

      "request body is provided with empty List" should {

        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(templateIds = List.empty))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected size of templateIds to be greater than or equal to 1, but got 0)")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateAssociationsService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(apiKeyTemplateAssociationsService)
          } yield ()
        }
      }

      "request body contains TemplateId which is an empty String" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "templateIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (Got value '\"\"' with wrong type, expecting string at 'templateIds[1]')")
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateAssociationsService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateAssociationsService)
          } yield ()
        }
      }

      "request body contains TemplateId which is not an UUID" should {

        val requestWithIncorrectPermissionId = request.withEntity(
          Map(
            "templateIds" -> List(
              "fd00156e-b56b-4d35-9d67-05bc3681ac82",
              "this-is-not-a-valid-uuid",
              "457cc79a-1357-4fa4-8d50-acd8b2e67d2a"
            )
          ).asJson
        )

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            "Invalid value for: body (Got value '\"this-is-not-a-valid-uuid\"' with wrong type, expecting string at 'templateIds[1]')"
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call ApiKeyTemplateAssociationsService" in authorizedFixture {
          for {
            _ <- adminUserRoutes.run(requestWithIncorrectPermissionId)
            _ = verifyZeroInteractions(apiKeyTemplateAssociationsService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ApiKeyTemplateAssociationsService" in authorizedFixture {
        apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(apiKeyTemplateAssociationsService).removeApiKeyTemplatesFromUser(
            eqTo(publicTenantId_1),
            eqTo(publicUserId_1),
            eqTo(requestBody.templateIds)
          )
        } yield ()
      }

      "return successful value returned by ApiKeyTemplateAssociationsService" in authorizedFixture {
        apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ = response.body shouldBe Stream.empty
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateAssociationsService returns successful IO with Left containing ReferencedApiKeyTemplateDoesNotExistError" in authorizedFixture {
        apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(
          Left(ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedApiKeyTemplateNotFound)
              )
            )
        } yield ()
      }

      "return Not Found when ApiKeyTemplateAssociationsService returns successful IO with Left containing ReferencedUserDoesNotExistError" in authorizedFixture {
        apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(Left(ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1)))

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
              )
            )
        } yield ()
      }

      "return Bad Request when ApiKeyTemplateAssociationsService returns successful IO with Left containing ApiKeyTemplatesUsersNotFoundError" in authorizedFixture {
        apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(
          Left(
            ApiKeyTemplatesUsersNotFoundError(
              List(
                apiKeyTemplatesUsersEntityWrite_1_1,
                apiKeyTemplatesUsersEntityWrite_1_2,
                apiKeyTemplatesUsersEntityWrite_1_3
              )
            )
          )
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.ApiKeyTemplatesUsersNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateAssociationsService returns failed IO" in authorizedFixture {
        apiKeyTemplateAssociationsService.removeApiKeyTemplatesFromUser(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on GET /admin/users/{userId}/templates" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1/templates")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApiKeyTemplateService" in authorizedFixture {
        apiKeyTemplateService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(Right(List.empty))

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(apiKeyTemplateService).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return successful value returned by UserService" when {

        "ApiKeyTemplateService returns an empty List" in authorizedFixture {
          apiKeyTemplateService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(Right(List.empty))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeyTemplatesResponse]
              .asserting(_ shouldBe GetMultipleApiKeyTemplatesResponse(templates = List.empty))
          } yield ()
        }

        "ApiKeyTemplateService returns a List with several elements" in authorizedFixture {
          apiKeyTemplateService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(
            Right(List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3))
          )

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeyTemplatesResponse]
              .asserting(
                _ shouldBe GetMultipleApiKeyTemplatesResponse(
                  List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
                )
              )
          } yield ()
        }
      }

      "return Bad Request when ApiKeyTemplateService returns successful IO with Left containing ReferencedUserDoesNotExistError" in authorizedFixture {
        apiKeyTemplateService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(
          Left(UserDoesNotExistError(publicTenantId_1, publicUserId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyTemplateService returns failed IO" in authorizedFixture {
        apiKeyTemplateService.getAllForUser(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminUserRoutes on GET /admin/users/{userId}/api-keys" when {

    val uri = Uri.unsafeFromString(s"/admin/users/$publicUserId_1/api-keys")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ApiKeyManagementService" in authorizedFixture {
        apiKeyManagementService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(Right(List.empty))

        for {
          _ <- adminUserRoutes.run(request)
          _ = verify(apiKeyManagementService).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return successful value returned by ManagementService" when {

        "ApiKeyManagementService returns empty List" in authorizedFixture {
          apiKeyManagementService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(Right(List.empty))

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response.as[GetMultipleApiKeysResponse].asserting(_ shouldBe GetMultipleApiKeysResponse(List.empty))
          } yield ()
        }

        "ApiKeyManagementService returns a List with several elements" in authorizedFixture {
          apiKeyManagementService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(
            Right(List(apiKeyData_1, apiKeyData_2, apiKeyData_3))
          )

          for {
            response <- adminUserRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleApiKeysResponse]
              .asserting(_ shouldBe GetMultipleApiKeysResponse(List(apiKeyData_1, apiKeyData_2, apiKeyData_3)))
          } yield ()
        }
      }

      "return Not Found when ApiKeyManagementService returns successful IO with Left containing ReferencedUserDoesNotExistError" in authorizedFixture {
        apiKeyManagementService.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(
          Left(UserDoesNotExistError(publicTenantId_1, publicUserId_1))
        )

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(
                Some(ApiErrorMessages.AdminApiKeyTemplatesUsers.SingleUser.ReferencedUserNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ApiKeyManagementService returns an exception" in authorizedFixture {
        apiKeyManagementService.getAllForUser(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        for {
          response <- adminUserRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
