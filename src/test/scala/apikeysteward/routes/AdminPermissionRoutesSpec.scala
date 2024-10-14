package apikeysteward.routes

import apikeysteward.base.testdata.ApplicationsTestData.publicApplicationId_1
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionNotFoundError
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.permission._
import apikeysteward.services.PermissionService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.{Headers, HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException
import java.util.UUID

class AdminPermissionRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val permissionService = mock[PermissionService]

  private val adminRoutes: HttpApp[IO] =
    new AdminPermissionRoutes(jwtAuthorizer, permissionService).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, permissionService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO], requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminRoutes, jwtAuthorizer, permissionService)(request, requiredPermissions)

  "AdminPermissionRoutes on POST /admin/applications/{applicationId}/permissions" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions")
    val requestBody = CreatePermissionRequest(name = permissionName_1, description = permissionDescription_1)

    val request = Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))
      .withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with applicationId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid/permissions")
        val requestWithIncorrectApplicationId =
          Request[IO](method = Method.POST, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
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

        "NOT call PermissionService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(permissionService)
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

        "NOT call PermissionService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(permissionService)
          } yield ()
        }
      }

      "request body is provided with name longer than 280 characters" should {

        val nameThatIsTooLong = List.fill(281)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameThatIsTooLong))
        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 280, but got: "$nameThatIsTooLong")"""
          )
        )

        "return Bad Request" in authorizedFixture {
          for {
            response <- adminRoutes.run(requestWithLongName)
            _ = response.status shouldBe Status.BadRequest
            _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
          } yield ()
        }

        "NOT call PermissionService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(permissionService)
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

        "NOT call PermissionService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(permissionService)
          } yield ()
        }
      }

      "request body is provided with description longer than 500 characters" should {

        val descriptionThatIsTooLong = List.fill(501)("A").mkString
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

        "NOT call PermissionService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(permissionService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call PermissionService" in authorizedFixture {
        permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
          permission_1.asRight
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(permissionService).createPermission(eqTo(publicApplicationId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by PermissionService" when {

        "provided with description" in authorizedFixture {
          permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
            permission_1.asRight
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreatePermissionResponse]
              .asserting(_ shouldBe CreatePermissionResponse(permission_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val permissionWithoutDescription = permission_1.copy(description = None)
          permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
            permissionWithoutDescription.asRight
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreatePermissionResponse]
              .asserting(_ shouldBe CreatePermissionResponse(permissionWithoutDescription))
          } yield ()
        }
      }

      "return Internal Server Error when PermissionService returns successful IO with Left containing PermissionAlreadyExistsError" in authorizedFixture {
        permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
          Left(PermissionAlreadyExistsError(publicPermissionIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when PermissionService returns successful IO with Left containing PermissionInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
          Left(PermissionInsertionErrorImpl(testSqlException))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Bad Request when PermissionService returns successful IO with Left containing PermissionAlreadyExistsForThisApplicationError" in authorizedFixture {
        val applicationId = 13L
        permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
          Left(PermissionAlreadyExistsForThisApplicationError(permissionName_1, applicationId))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminPermission.PermissionAlreadyExistsForThisApplication)
              )
            )
        } yield ()
      }

      "return Bad Request when PermissionService returns successful IO with Left containing ReferencedApplicationDoesNotExistError" in authorizedFixture {
        permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.pure(
          Left(ReferencedApplicationDoesNotExistError(publicApplicationId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminPermission.ReferencedApplicationNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when PermissionService returns failed IO" in authorizedFixture {
        permissionService.createPermission(any[UUID], any[CreatePermissionRequest]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminPermissionRoutes on DELETE /admin/applications/{applicationId}/permissions/{permissionId}" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions/$publicPermissionId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    "provided with applicationId which is not an UUID" should {
      "return Bad Request" in {
        val uri =
          Uri.unsafeFromString(s"/admin/applications/this-is-not-a-valid-uuid/permissions/$publicPermissionId_1")
        val requestWithIncorrectApplicationId =
          Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectApplicationId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }
    }

    "provided with permissionId which is not an UUID" should {
      "return Bad Request" in {
        val uri =
          Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions/this-is-not-a-valid-uuid")
        val requestWithIncorrectPermissionId =
          Request[IO](method = Method.DELETE, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectPermissionId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter permissionId"))
            )
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call PermissionService" in authorizedFixture {
        permissionService.deletePermission(any[ApplicationId], any[PermissionId]) returns IO.pure(permission_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(permissionService).deletePermission(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
        } yield ()
      }

      "return successful value returned by PermissionService" in authorizedFixture {
        permissionService.deletePermission(any[ApplicationId], any[PermissionId]) returns IO.pure(permission_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeletePermissionResponse]
            .asserting(_ shouldBe DeletePermissionResponse(permission_1))
        } yield ()
      }

      "return Not Found when PermissionService returns successful IO with Left containing PermissionNotFoundError" in authorizedFixture {
        permissionService.deletePermission(any[ApplicationId], any[PermissionId]) returns IO.pure(
          Left(PermissionNotFoundError(publicApplicationId_1, publicPermissionId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminPermission.PermissionNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when PermissionService returns failed IO" in authorizedFixture {
        permissionService.deletePermission(any[ApplicationId], any[PermissionId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminPermissionRoutes on GET /admin/applications/{applicationId}/permissions/{permissionId}" when {

    val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions/$publicPermissionId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "provided with applicationId which is not an UUID" should {
      "return Bad Request" in {
        val uri =
          Uri.unsafeFromString(s"/admin/applications/this-is-not-a-valid-uuid/permissions/$publicPermissionId_1")
        val requestWithIncorrectPermissionId =
          Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectPermissionId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }
    }

    "provided with permissionId which is not an UUID" should {
      "return Bad Request" in {
        val uri =
          Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions/this-is-not-a-valid-uuid")
        val requestWithIncorrectPermissionId =
          Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectPermissionId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter permissionId"))
            )
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call PermissionService" in authorizedFixture {
        permissionService.getBy(any[ApplicationId], any[PermissionId]) returns IO.pure(permission_1.some)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(permissionService).getBy(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
        } yield ()
      }

      "return successful value returned by PermissionService" in authorizedFixture {
        permissionService.getBy(any[ApplicationId], any[PermissionId]) returns IO.pure(permission_1.some)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSinglePermissionResponse]
            .asserting(_ shouldBe GetSinglePermissionResponse(permission_1))
        } yield ()
      }

      "return Not Found when PermissionService returns empty Option" in authorizedFixture {
        permissionService.getBy(any[ApplicationId], any[PermissionId]) returns IO.pure(none)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminPermission.PermissionNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when PermissionService returns failed IO" in authorizedFixture {
        permissionService.getBy(any[ApplicationId], any[PermissionId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminPermissionRoutes on GET /admin/applications/{applicationId}/permissions" when {

    val nameFragment = "test:name:fragment:123"

    val uri = Uri
      .unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions")
      .withQueryParam("name", nameFragment)
    val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    "provided with applicationId which is not an UUID" should {
      "return Bad Request" in {
        val uri = Uri.unsafeFromString("/admin/applications/this-is-not-a-valid-uuid/permissions")
        val requestWithIncorrectPermissionId =
          Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

        for {
          response <- adminRoutes.run(requestWithIncorrectPermissionId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter applicationId"))
            )
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call PermissionService providing nameFragment when request contains 'name' query param" in authorizedFixture {
        permissionService.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(permissionService).getAllBy(eqTo(publicApplicationId_1))(eqTo(Some(nameFragment)))
        } yield ()
      }

      "call PermissionService providing empty Option when request contains NO query param" in authorizedFixture {
        val uri = Uri.unsafeFromString(s"/admin/applications/$publicApplicationId_1/permissions")
        val request = Request[IO](method = Method.GET, uri = uri, headers = Headers(authorizationHeader))

        permissionService.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(permissionService).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
        } yield ()
      }

      "return successful value returned by PermissionService" when {

        "PermissionService returns an empty List" in authorizedFixture {
          permissionService.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultiplePermissionsResponse]
              .asserting(_ shouldBe GetMultiplePermissionsResponse(permissions = List.empty))
          } yield ()
        }

        "PermissionService returns a List with several elements" in authorizedFixture {
          permissionService.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(
            List(permission_1, permission_2, permission_3)
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultiplePermissionsResponse]
              .asserting(
                _ shouldBe GetMultiplePermissionsResponse(permissions = List(permission_1, permission_2, permission_3))
              )
          } yield ()
        }
      }

      "return Internal Server Error when PermissionService returns failed IO" in authorizedFixture {
        permissionService.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
