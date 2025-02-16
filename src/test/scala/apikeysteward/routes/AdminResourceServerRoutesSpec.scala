package apikeysteward.routes

import apikeysteward.base.testdata.PermissionsTestData.{
  createPermissionRequest_1,
  createPermissionRequest_2,
  createPermissionRequest_3
}
import apikeysteward.base.testdata.ResourceServersTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantIdStr_1, publicTenantId_1}
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.errors.ResourceServerDbError._
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.auth.JwtAuthorizer
import apikeysteward.routes.auth.JwtAuthorizer.Permission
import apikeysteward.routes.auth.model.JwtPermissions
import apikeysteward.routes.definitions.ApiErrorMessages
import apikeysteward.routes.model.admin.resourceserver._
import apikeysteward.services.ResourceServerService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId, none}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
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

class AdminResourceServerRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with RoutesSpecBase {

  private val jwtAuthorizer = mock[JwtAuthorizer]
  private val resourceServerService = mock[ResourceServerService]

  private val adminRoutes: HttpApp[IO] =
    new AdminResourceServerRoutes(jwtAuthorizer, resourceServerService).allRoutes.orNotFound

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(jwtAuthorizer, resourceServerService)
  }

  private def authorizedFixture[T](test: => IO[T]): IO[T] =
    authorizedFixture(jwtAuthorizer)(test)

  private def runCommonJwtTests(request: Request[IO], requiredPermissions: Set[Permission]): Unit =
    runCommonJwtTests(adminRoutes, jwtAuthorizer, List(resourceServerService))(request, requiredPermissions)

  private def runCommonTenantIdHeaderTests(request: Request[IO]): Unit =
    runCommonTenantIdHeaderTests(adminRoutes, jwtAuthorizer, List(resourceServerService))(request)

  "AdminResourceServerRoutes on POST /admin/resource-servers" when {

    val uri = Uri.unsafeFromString("/admin/resource-servers")
    val requestBody = CreateResourceServerRequest(
      name = resourceServerName_1,
      description = resourceServerDescription_1,
      permissions = List(createPermissionRequest_1, createPermissionRequest_2, createPermissionRequest_3)
    )

    val request = Request[IO](method = Method.POST, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(resourceServerService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ResourceServerService" in authorizedFixture {
        resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO.pure(
          resourceServer_1.asRight
        )

        for {
          _ <- adminRoutes.run(request)
          _ = verify(resourceServerService).createResourceServer(eqTo(publicTenantId_1), eqTo(requestBody))
        } yield ()
      }

      "return successful value returned by ResourceServerService" when {

        "provided with description" in authorizedFixture {
          resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO.pure(
            resourceServer_1.asRight
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateResourceServerResponse]
              .asserting(_ shouldBe CreateResourceServerResponse(resourceServer_1))
          } yield ()
        }

        "provided with NO description" in authorizedFixture {
          val resourceServerWithoutDescription = resourceServer_1.copy(description = None)
          resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO.pure(
            resourceServerWithoutDescription.asRight
          )

          val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

          for {
            response <- adminRoutes.run(requestWithoutDescription)
            _ = response.status shouldBe Status.Created
            _ <- response
              .as[CreateResourceServerResponse]
              .asserting(_ shouldBe CreateResourceServerResponse(resourceServerWithoutDescription))
          } yield ()
        }
      }

      "return Internal Server Error when ResourceServerService returns successful IO with Left containing ResourceServerAlreadyExistsError" in authorizedFixture {
        resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO.pure(
          Left(ResourceServerAlreadyExistsError(publicResourceServerIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Internal Server Error when ResourceServerService returns successful IO with Left containing ResourceServerInsertionErrorImpl" in authorizedFixture {
        val testSqlException = new SQLException("Test SQL Exception")
        resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO.pure(
          Left(ResourceServerInsertionErrorImpl(testSqlException))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }

      "return Bad Request when ResourceServerService returns successful IO with Left containing ReferencedTenantDoesNotExistError" in authorizedFixture {
        resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO.pure(
          Left(ReferencedTenantDoesNotExistError(publicResourceServerId_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(
                Some(ApiErrorMessages.AdminResourceServer.ReferencedTenantNotFound)
              )
            )
        } yield ()
      }

      "return Internal Server Error when ResourceServerService returns failed IO" in authorizedFixture {
        resourceServerService.createResourceServer(any[TenantId], any[CreateResourceServerRequest]) returns IO
          .raiseError(
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

  "AdminResourceServerRoutes on PUT /admin/resource-servers/{resourceServerId}" when {

    val uri = Uri.unsafeFromString(s"/admin/resource-servers/$publicResourceServerId_1")
    val requestBody =
      UpdateResourceServerRequest(name = resourceServerName_1, description = resourceServerDescription_1)

    val request = Request[IO](method = Method.PUT, uri = uri, headers = allHeaders).withEntity(requestBody.asJson)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "provided with resourceServerId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/resource-servers/this-is-not-a-valid-uuid")
      val requestWithIncorrectResourceServerId = request.withUri(uri)

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectResourceServerId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter resourceServerId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectResourceServerId)
          _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithOnlyWhiteCharacters)
            _ = verifyZeroInteractions(resourceServerService)
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

        "NOT call ResourceServerService" in authorizedFixture {
          for {
            _ <- adminRoutes.run(requestWithLongName)
            _ = verifyZeroInteractions(resourceServerService)
          } yield ()
        }
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken and request body is correct" should {

      "call ResourceServerService" in authorizedFixture {
        resourceServerService.updateResourceServer(
          any[TenantId],
          any[ResourceServerId],
          any[UpdateResourceServerRequest]
        ) returns IO.pure(resourceServer_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(resourceServerService).updateResourceServer(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1),
            eqTo(requestBody)
          )
        } yield ()
      }

      "return successful value returned by ResourceServerService" in authorizedFixture {
        resourceServerService.updateResourceServer(
          any[TenantId],
          any[ResourceServerId],
          any[UpdateResourceServerRequest]
        ) returns IO.pure(resourceServer_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[UpdateResourceServerResponse]
            .asserting(_ shouldBe UpdateResourceServerResponse(resourceServer_1))
        } yield ()
      }

      "return Not Found when ResourceServerService returns successful IO with Left containing ResourceServerNotFoundError" in authorizedFixture {
        resourceServerService.updateResourceServer(
          any[TenantId],
          any[ResourceServerId],
          any[UpdateResourceServerRequest]
        ) returns IO.pure(Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ResourceServerNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ResourceServerService returns failed IO" in authorizedFixture {
        resourceServerService.updateResourceServer(
          any[TenantId],
          any[ResourceServerId],
          any[UpdateResourceServerRequest]
        ) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminResourceServerRoutes on DELETE /admin/resource-servers/{resourceServerId}" when {

    val uri = Uri.unsafeFromString(s"/admin/resource-servers/$publicResourceServerId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.WriteAdmin))

    runCommonTenantIdHeaderTests(request)

    "provided with resourceServerId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/resource-servers/this-is-not-a-valid-uuid")
      val requestWithIncorrectResourceServerId = request.withUri(uri)

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectResourceServerId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter resourceServerId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectResourceServerId)
          _ = verifyZeroInteractions(resourceServerService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ResourceServerService" in authorizedFixture {
        resourceServerService.deleteResourceServer(any[TenantId], any[ResourceServerId]) returns
          IO.pure(resourceServer_1.asRight)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(resourceServerService).deleteResourceServer(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))
        } yield ()
      }

      "return successful value returned by ResourceServerService" in authorizedFixture {
        resourceServerService.deleteResourceServer(any[TenantId], any[ResourceServerId]) returns
          IO.pure(resourceServer_1.asRight)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[DeleteResourceServerResponse]
            .asserting(_ shouldBe DeleteResourceServerResponse(resourceServer_1))
        } yield ()
      }

      "return Not Found when ResourceServerService returns successful IO with Left containing ResourceServerNotFoundError" in authorizedFixture {
        resourceServerService.deleteResourceServer(any[TenantId], any[ResourceServerId]) returns IO.pure(
          Left(ResourceServerNotFoundError(publicResourceServerIdStr_1))
        )

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ResourceServerNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ResourceServerService returns failed IO" in authorizedFixture {
        resourceServerService.deleteResourceServer(any[TenantId], any[ResourceServerId]) returns
          IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminResourceServerRoutes on GET /admin/resource-servers/{resourceServerId}" when {

    val uri = Uri.unsafeFromString(s"/admin/resource-servers/$publicResourceServerId_1")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "provided with resourceServerId which is not an UUID" should {

      val uri = Uri.unsafeFromString("/admin/resource-servers/this-is-not-a-valid-uuid")
      val requestWithIncorrectResourceServerId = request.withUri(uri)

      "return Bad Request" in {
        for {
          response <- adminRoutes.run(requestWithIncorrectResourceServerId)
          _ = response.status shouldBe Status.BadRequest
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.badRequestErrorInfo(Some("Invalid value for: path parameter resourceServerId"))
            )
        } yield ()
      }

      "NOT call ApiKeyTemplateService" in authorizedFixture {
        for {
          _ <- adminRoutes.run(requestWithIncorrectResourceServerId)
          _ = verifyZeroInteractions(resourceServerService)
        } yield ()
      }
    }

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ResourceServerService" in authorizedFixture {
        resourceServerService.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(resourceServer_1.some)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(resourceServerService).getBy(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))
        } yield ()
      }

      "return successful value returned by ResourceServerService" in authorizedFixture {
        resourceServerService.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(resourceServer_1.some)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response
            .as[GetSingleResourceServerResponse]
            .asserting(_ shouldBe GetSingleResourceServerResponse(resourceServer_1))
        } yield ()
      }

      "return Not Found when ResourceServerService returns empty Option" in authorizedFixture {
        resourceServerService.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(none)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.NotFound
          _ <- response
            .as[ErrorInfo]
            .asserting(
              _ shouldBe ErrorInfo.notFoundErrorInfo(Some(ApiErrorMessages.AdminResourceServer.ResourceServerNotFound))
            )
        } yield ()
      }

      "return Internal Server Error when ResourceServerService returns failed IO" in authorizedFixture {
        resourceServerService.getBy(any[TenantId], any[ResourceServerId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

  "AdminResourceServerRoutes on GET /admin/resource-servers" when {

    val uri = Uri.unsafeFromString(s"/admin/resource-servers")
    val request = Request[IO](method = Method.GET, uri = uri, headers = allHeaders)

    runCommonJwtTests(request, Set(JwtPermissions.ReadAdmin))

    runCommonTenantIdHeaderTests(request)

    "JwtAuthorizer returns Right containing JsonWebToken" should {

      "call ResourceServerService" in authorizedFixture {
        resourceServerService.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        for {
          _ <- adminRoutes.run(request)
          _ = verify(resourceServerService).getAllForTenant(eqTo(UUID.fromString(tenantIdHeader.value)))
        } yield ()
      }

      "return successful value returned by ResourceServerService" when {

        "ResourceServerService returns an empty List" in authorizedFixture {
          resourceServerService.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleResourceServersResponse]
              .asserting(_ shouldBe GetMultipleResourceServersResponse(resourceServers = List.empty))
          } yield ()
        }

        "ResourceServerService returns a List with several elements" in authorizedFixture {
          resourceServerService.getAllForTenant(any[TenantId]) returns IO.pure(
            List(resourceServer_1, resourceServer_2, resourceServer_3)
          )

          for {
            response <- adminRoutes.run(request)
            _ = response.status shouldBe Status.Ok
            _ <- response
              .as[GetMultipleResourceServersResponse]
              .asserting(
                _ shouldBe GetMultipleResourceServersResponse(resourceServers =
                  List(resourceServer_1, resourceServer_2, resourceServer_3)
                )
              )
          } yield ()
        }
      }

      "return Internal Server Error when ResourceServerService returns failed IO" in authorizedFixture {
        resourceServerService.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.InternalServerError
          _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
        } yield ()
      }
    }
  }

}
