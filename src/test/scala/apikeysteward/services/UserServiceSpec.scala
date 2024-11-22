package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{apiKeyTemplate_1, publicTemplateId_1}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenant_1}
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError._
import apikeysteward.model.RepositoryErrors.UserDbError.UserNotFoundError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKeyTemplate, Tenant, User}
import apikeysteward.repositories.{ApiKeyTemplateRepository, TenantRepository, UserRepository}
import apikeysteward.routes.model.admin.user.CreateUserRequest
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException

class UserServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with FixedClock with BeforeAndAfterEach {

  private val userRepository = mock[UserRepository]
  private val tenantRepository = mock[TenantRepository]
  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]

  private val userService = new UserService(userRepository, tenantRepository, apiKeyTemplateRepository)

  override def beforeEach(): Unit =
    reset(userRepository, tenantRepository, apiKeyTemplateRepository)

  private val testException = new RuntimeException("Test Exception")

  "UserService on createUser" when {

    val createUserRequest = CreateUserRequest(userId = publicUserId_1)

    "everything works correctly" should {

      "call UserRepository" in {
        userRepository.insert(any[TenantId], any[User]) returns IO.pure(Right(user_1))

        for {
          _ <- userService.createUser(publicTenantId_1, createUserRequest)

          _ = verify(userRepository).insert(eqTo(publicTenantId_1), eqTo(user_1))
        } yield ()
      }

      "return the newly created User returned by UserRepository" in {
        userRepository.insert(any[TenantId], any[User]) returns IO.pure(Right(user_1))

        userService
          .createUser(publicTenantId_1, createUserRequest)
          .asserting(_ shouldBe Right(user_1))
      }
    }

    val tenantId = 13L
    val testSqlException = new SQLException("Test SQL Exception")

    Seq(
      UserAlreadyExistsForThisTenantError(publicUserId_1, tenantId),
      ReferencedTenantDoesNotExistError(publicTenantId_1),
      UserInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"UserRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "NOT call UserRepository again" in {
          userRepository.insert(any[TenantId], any[User]) returns IO.pure(Left(insertionError))

          for {
            _ <- userService.createUser(publicTenantId_1, createUserRequest)

            _ = verify(userRepository).insert(eqTo(publicTenantId_1), eqTo(user_1))
          } yield ()
        }

        "return failed IO containing this error" in {
          userRepository.insert(any[TenantId], any[User]) returns IO.pure(Left(insertionError))

          userService.createUser(publicTenantId_1, createUserRequest).asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "UserRepository returns failed IO" should {

      "NOT call UserRepository again" in {
        userRepository.insert(any[TenantId], any[User]) returns IO.raiseError(testException)

        for {
          _ <- userService.createUser(publicTenantId_1, createUserRequest).attempt

          _ = verify(userRepository).insert(eqTo(publicTenantId_1), eqTo(user_1))
        } yield ()
      }

      "return failed IO containing this exception" in {
        userRepository.insert(any[TenantId], any[User]) returns IO.raiseError(testException)

        userService
          .createUser(publicTenantId_1, createUserRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserService on deleteUser" should {

    "call UserRepository" in {
      userRepository.delete(any[TenantId], any[UserId]) returns IO.pure(Right(user_1))

      for {
        _ <- userService.deleteUser(publicTenantId_1, publicUserId_1)

        _ = verify(userRepository).delete(eqTo(publicTenantId_1), eqTo(publicUserId_1))
      } yield ()
    }

    "return value returned by UserRepository" when {

      "UserRepository returns Right" in {
        userRepository.delete(any[TenantId], any[UserId]) returns IO.pure(Right(user_1))

        userService
          .deleteUser(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe Right(user_1))
      }

      "UserRepository returns Left" in {
        userRepository.delete(any[TenantId], any[UserId]) returns IO.pure(
          Left(UserNotFoundError(publicTenantId_1, publicUserId_1))
        )

        userService
          .deleteUser(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_1, publicUserId_1)))
      }
    }

    "return failed IO" when {
      "UserRepository returns failed IO" in {
        userRepository.delete(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        userService
          .deleteUser(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserService on getBy(:tenantId, :userId)" should {

    "call UserRepository" in {
      userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Some(user_1))

      for {
        _ <- userService.getBy(publicTenantId_1, publicUserId_1)

        _ = verify(userRepository).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
      } yield ()
    }

    "return the value returned by UserRepository" when {

      "UserRepository returns empty Option" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(None)

        userService.getBy(publicTenantId_1, publicUserId_1).asserting(_ shouldBe None)
      }

      "UserRepository returns non-empty Option" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Some(user_1))

        userService.getBy(publicTenantId_1, publicUserId_1).asserting(_ shouldBe Some(user_1))
      }
    }

    "return failed IO" when {
      "UserRepository returns failed IO" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        userService
          .getBy(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserService on getAllForTenant" when {

    "everything works correctly" should {

      "call TenantRepository and UserRepository" in {
        tenantRepository.getBy(any[TenantId]) returns IO.pure(Option(tenant_1))
        userRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        for {
          _ <- userService.getAllForTenant(publicTenantId_1)

          _ = verify(tenantRepository).getBy(eqTo(publicTenantId_1))
          _ = verify(userRepository).getAllForTenant(eqTo(publicTenantId_1))
        } yield ()
      }

      "return the value returned by UserRepository" when {

        "UserRepository returns empty List" in {
          tenantRepository.getBy(any[TenantId]) returns IO.pure(Option(tenant_1))
          userRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

          userService
            .getAllForTenant(publicTenantId_1)
            .asserting(_ shouldBe Right(List.empty[User]))
        }

        "UserRepository returns non-empty List" in {
          tenantRepository.getBy(any[TenantId]) returns IO.pure(Option(tenant_1))
          userRepository.getAllForTenant(any[TenantId]) returns IO.pure(List(user_1, user_2, user_3))

          userService
            .getAllForTenant(publicTenantId_1)
            .asserting(_ shouldBe Right(List(user_1, user_2, user_3)))
        }
      }
    }

    "TenantRepository returns empty Option" should {

      "NOT call UserRepository" in {
        tenantRepository.getBy(any[TenantId]) returns IO.pure(none[Tenant])

        for {
          _ <- userService.getAllForTenant(publicTenantId_1)

          _ = verifyZeroInteractions(userRepository)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        tenantRepository.getBy(any[TenantId]) returns IO.pure(none[Tenant])

        userService
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "UserRepository returns failed IO" should {
      "return failed IO" in {
        tenantRepository.getBy(any[TenantId]) returns IO.pure(Option(tenant_1))
        userRepository.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        userService
          .getAllForTenant(publicTenantId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserService on getAllForTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplateRepository and UserRepository" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
        userRepository.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(List.empty)

        for {
          _ <- userService.getAllForTemplate(publicTemplateId_1)

          _ = verify(apiKeyTemplateRepository).getBy(eqTo(publicTemplateId_1))
          _ = verify(userRepository).getAllForTemplate(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return the value returned by UserRepository" when {

        "UserRepository returns empty List" in {
          apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
          userRepository.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(List.empty)

          userService.getAllForTemplate(publicTemplateId_1).asserting(_ shouldBe Right(List.empty[User]))
        }

        "UserRepository returns non-empty List" in {
          apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
          userRepository.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.pure(List(user_1, user_2, user_3))

          userService.getAllForTemplate(publicTemplateId_1).asserting(_ shouldBe Right(List(user_1, user_2, user_3)))
        }
      }
    }

    "ApiKeyTemplateRepository returns empty Option" should {

      "NOT call UserRepository" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(none[ApiKeyTemplate])

        for {
          _ <- userService.getAllForTemplate(publicTemplateId_1)

          _ = verifyZeroInteractions(userRepository)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(none[ApiKeyTemplate])

        userService
          .getAllForTemplate(publicTemplateId_1)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "UserRepository returns failed IO" should {
      "return failed IO containing the same exception" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
        userRepository.getAllForTemplate(any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        userService
          .getAllForTemplate(publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
