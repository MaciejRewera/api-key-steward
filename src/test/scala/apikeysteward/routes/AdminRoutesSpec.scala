package apikeysteward.routes

import apikeysteward.base.TestData._
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.db.DbCommons.ApiKeyDeletionError.{ApiKeyDataNotFound, CannotDeleteApiKeyDataError}
import apikeysteward.routes.definitions.AdminEndpoints.ErrorMessages
import apikeysteward.routes.model.admin.{CreateApiKeyAdminRequest, CreateApiKeyAdminResponse, DeleteApiKeyAdminResponse}
import apikeysteward.services.AdminService
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{HttpApp, Method, Request, Status, Uri}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, verify}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class AdminRoutesSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  private val adminService = mock[AdminService[String]]
  private val adminRoutes: HttpApp[IO] = new AdminRoutes(adminService).allRoutes.orNotFound

  private val testException = new RuntimeException("Test Exception")

  "AdminRoutes on POST /admin/users/{userId}/api-key" should {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key")
    val requestBody = CreateApiKeyAdminRequest(
      name = name,
      description = description,
      ttl = ttlSeconds,
      scopes = List(scopeRead_1, scopeWrite_1)
    )
    val request = Request[IO](method = Method.POST, uri = uri).withEntity(requestBody.asJson)

    "call AdminService" in {
      adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

      for {
        _ <- adminRoutes.run(request)
        _ = verify(adminService).createApiKey(eqTo(userId_1), eqTo(requestBody))
      } yield ()
    }

    "return the value returned by AdminService" when {

      "provided with description" in {
        adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Created
          _ <- response
            .as[CreateApiKeyAdminResponse]
            .asserting(_ shouldBe CreateApiKeyAdminResponse(apiKey_1, apiKeyData_1))
        } yield ()
      }

      "provided with NO description" in {
        adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

        val requestWithoutDescription = request.withEntity(requestBody.copy(description = None))

        for {
          response <- adminRoutes.run(requestWithoutDescription)
          _ = response.status shouldBe Status.Created
          _ <- response
            .as[CreateApiKeyAdminResponse]
            .asserting(_ shouldBe CreateApiKeyAdminResponse(apiKey_1, apiKeyData_1))
        } yield ()
      }
    }

    "return Bad Request" when {

      "provided with empty name" in {
        val nameEmpty = ""
        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = nameEmpty))

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        for {
          response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "provided with name containing only white characters" in {
        val nameWithOnlyWhiteCharacters = "  \n   \n\n "
        val requestWithOnlyWhiteCharacters = request.withEntity(requestBody.copy(name = nameWithOnlyWhiteCharacters))

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"""Invalid value for: body (expected name to have length greater than or equal to 1, but got: "")""")
        )

        for {
          response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "provided with name longer than 250 characters" in {
        val nameWhichIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(name = nameWhichIsTooLong))

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"""Invalid value for: body (expected name to have length less than or equal to 250, but got: "$nameWhichIsTooLong")"""
          )
        )

        for {
          response <- adminRoutes.run(requestWithLongName)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "provided with description containing only white characters" in {
        val descriptionWithOnlyWhiteCharacters = "  \n   \n\n "
        val requestWithOnlyWhiteCharacters =
          request.withEntity(requestBody.copy(description = Some(descriptionWithOnlyWhiteCharacters)))

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(s"Invalid value for: body (expected description to pass validation, but got: Some())")
        )

        for {
          response <- adminRoutes.run(requestWithOnlyWhiteCharacters)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "provided with description longer than 250 characters" in {
        val descriptionWhichIsTooLong = List.fill(251)("A").mkString
        val requestWithLongName = request.withEntity(requestBody.copy(description = Some(descriptionWhichIsTooLong)))

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some(
            s"Invalid value for: body (expected description to pass validation, but got: Some($descriptionWhichIsTooLong))"
          )
        )

        for {
          response <- adminRoutes.run(requestWithLongName)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }

      "provided with negative ttl value" in {
        val requestWithNegativeTtl = request.withEntity(requestBody.copy(ttl = -1))

        val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(
          Some("Invalid value for: body (expected ttl to be greater than or equal to 0, but got -1)")
        )

        for {
          response <- adminRoutes.run(requestWithNegativeTtl)
          _ = response.status shouldBe Status.BadRequest
          _ <- response.as[ErrorInfo].asserting(_ shouldBe expectedErrorInfo)
        } yield ()
      }
    }

    "return Internal Server Error when AdminService returns an exception" in {
      adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.raiseError(testException)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
      } yield ()
    }
  }

  "AdminRoutes on GET /admin/users/{userId}/api-key" should {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key")
    val request = Request[IO](method = Method.GET, uri = uri)

    "call AdminService" in {
      adminService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

      for {
        _ <- adminRoutes.run(request)
        _ = verify(adminService).getAllApiKeysFor(eqTo(userId_1))
      } yield ()
    }

    "return Not Found when AdminService returns empty List" in {
      adminService.getAllApiKeysFor(any[String]) returns IO.pure(List.empty)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.NotFound
        _ <- response
          .as[ErrorInfo]
          .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.GetAllApiKeysForUserNotFound)))
      } yield ()
    }

    "return Ok and all ApiKeyData when AdminService returns non-empty List" in {
      adminService.getAllApiKeysFor(any[String]) returns IO.pure(List(apiKeyData_1, apiKeyData_2, apiKeyData_3))

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.Ok
        _ <- response.as[List[ApiKeyData]].asserting(_ shouldBe List(apiKeyData_1, apiKeyData_2, apiKeyData_3))
      } yield ()
    }

    "return Internal Server Error when AdminService returns an exception" in {
      adminService.getAllApiKeysFor(any[String]) returns IO.raiseError(testException)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
      } yield ()
    }
  }

  "AdminRoutes on GET /admin/users" should {

    val uri = uri"/admin/users"
    val request = Request[IO](method = Method.GET, uri = uri)

    "call AdminService" in {
      adminService.getAllUserIds returns IO.pure(List.empty)

      for {
        _ <- adminRoutes.run(request)
        _ = verify(adminService).getAllUserIds
      } yield ()
    }

    "return the value returned by AdminService" when {

      "it is an empty List" in {
        adminService.getAllUserIds returns IO.pure(List.empty)

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[List[String]].asserting(_ shouldBe List.empty[String])
        } yield ()
      }

      "it is a List with several elements" in {
        adminService.getAllUserIds returns IO.pure(List(userId_1, userId_2, userId_3))

        for {
          response <- adminRoutes.run(request)
          _ = response.status shouldBe Status.Ok
          _ <- response.as[List[String]].asserting(_ shouldBe List(userId_1, userId_2, userId_3))
        } yield ()
      }
    }

    "return Internal Server Error when AdminService returns an exception" in {
      adminService.getAllUserIds returns IO.raiseError(testException)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
      } yield ()
    }
  }

  "AdminRoutes on DELETE /admin/users/{userId}/api-key/{publicKeyId}" should {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key/$publicKeyId_1")
    val request = Request[IO](method = Method.DELETE, uri = uri)

    "call AdminService" in {
      adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

      for {
        _ <- adminRoutes.run(request)
        _ = verify(adminService).deleteApiKey(eqTo(userId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "return Ok and ApiKeyData returned by AdminService" in {
      adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(Right(apiKeyData_1))

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.Ok
        _ <- response.as[DeleteApiKeyAdminResponse].asserting(_ shouldBe DeleteApiKeyAdminResponse(apiKeyData_1))
      } yield ()
    }

    "return Not Found when AdminService returns Left containing ApiKeyDataNotFound" in {
      adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(
        Left(ApiKeyDataNotFound(userId_1, publicKeyId_1))
      )

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.NotFound
        _ <- response
          .as[ErrorInfo]
          .asserting(_ shouldBe ErrorInfo.notFoundErrorInfo(Some(ErrorMessages.DeleteApiKeyNotFound)))
      } yield ()
    }

    "return Internal Server Error when AdminService returns Left containing CannotDeleteApiKeyDataError" in {
      adminService.deleteApiKey(any[String], any[UUID]) returns IO.pure(
        Left(CannotDeleteApiKeyDataError(userId_1, publicKeyId_1))
      )

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response
          .as[ErrorInfo]
          .asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
      } yield ()
    }

    "return Bad Request when provided with publicKeyId which is not an UUID" in {
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

    "return Internal Server Error when AdminService returns an exception" in {
      adminService.deleteApiKey(any[String], any[UUID]) returns IO.raiseError(testException)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorInfo())
      } yield ()
    }
  }
}
