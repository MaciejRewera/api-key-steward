package apikeysteward.routes

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData._
import apikeysteward.model.ApiKeyData
import apikeysteward.repositories.DoobieUnitSpec
import apikeysteward.routes.definitions.AdminEndpoints.ErrorMessages
import apikeysteward.routes.model.admin.{CreateApiKeyAdminRequest, CreateApiKeyAdminResponse}
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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class AdminRoutesSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach {

  private val adminService = mock[AdminService[String]]

  private val adminRoutes: HttpApp[IO] = new AdminRoutes(adminService).allRoutes.orNotFound

  private val testException = new RuntimeException("Test Exception")

  "AdminRoutes on POST /admin/users/{userId}/api-key" should {

    val uri = Uri.unsafeFromString(s"/admin/users/$userId_1/api-key")
    val requestBody = CreateApiKeyAdminRequest(
      name = name,
      description = description,
      ttl = ttlSeconds
    )
    val request = Request[IO](method = Method.POST, uri = uri).withEntity(requestBody.asJson)

    "call AdminService" in {
      adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

      for {
        _ <- adminRoutes.run(request)
        _ = verify(adminService).createApiKey(eqTo(userId_1), eqTo(requestBody))
      } yield ()
    }

    "return the value returned by AdminService" in {
      adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.pure(apiKey_1, apiKeyData_1)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.Created
        _ <- response
          .as[CreateApiKeyAdminResponse]
          .asserting(_ shouldBe CreateApiKeyAdminResponse(apiKey_1, apiKeyData_1))
      } yield ()
    }

    "return error when provided with negative ttl value" in {
      val requestWithNegativeTtl = request.withEntity(requestBody.copy(ttl = -1))

      for {
        response <- adminRoutes.run(requestWithNegativeTtl)
        _ = response.status shouldBe Status.BadRequest
        _ <- response
          .as[ErrorInfo]
          .asserting(
            _ shouldBe ErrorInfo.badRequestErrorDetail(
              Some("Invalid value for: body (expected ttl to be greater than or equal to 0, but got -1)")
            )
          )
      } yield ()
    }

    "return Internal Server Error when AdminService returns an exception" in {
      adminService.createApiKey(any[String], any[CreateApiKeyAdminRequest]) returns IO.raiseError(testException)

      for {
        response <- adminRoutes.run(request)
        _ = response.status shouldBe Status.InternalServerError
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorDetail())
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
          .asserting(_ shouldBe ErrorInfo.notFoundErrorDetail(Some(ErrorMessages.GetAllApiKeysForUserNotFound)))
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
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorDetail())
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
        _ <- response.as[ErrorInfo].asserting(_ shouldBe ErrorInfo.internalServerErrorDetail())
      } yield ()
    }
  }
}
