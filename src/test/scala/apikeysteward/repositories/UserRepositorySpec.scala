package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantEntityRead_1}
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError.{
  ReferencedTenantDoesNotExistError,
  UserInsertionErrorImpl
}
import apikeysteward.model.RepositoryErrors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.{TenantEntity, UserEntity}
import apikeysteward.repositories.db.{TenantDb, UserDb}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxApplicativeErrorId, catsSyntaxApplicativeId, catsSyntaxEitherId, none}
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.sql.SQLException

class UserRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val tenantDb = mock[TenantDb]
  private val userDb = mock[UserDb]

  private val userRepository = new UserRepository(tenantDb, userDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(tenantDb, userDb)

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, UserEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, UserEntity.Read]]

  "UserRepository on insert" when {

    val tenantId = 13L
    val tenantEntityReadWrapped = Option(tenantEntityRead_1.copy(id = tenantId)).pure[doobie.ConnectionIO]

    val userEntityReadWrapped = userEntityRead_1.asRight[UserInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")
    val userInsertionError: UserInsertionError = UserInsertionErrorImpl(testSqlException)
    val userInsertionErrorWrapped = userInsertionError.asLeft[UserEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb and UserDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.insert(any[UserEntity.Write]) returns userEntityReadWrapped

        for {
          _ <- userRepository.insert(publicTenantId_1, user_1)

          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
          expectedUserEntityWrite = UserEntity.Write(tenantId = tenantId, publicUserId = publicUserId_1)
          _ = verify(userDb).insert(eqTo(expectedUserEntityWrite))
        } yield ()
      }

      "return Right containing User" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.insert(any[UserEntity.Write]) returns userEntityReadWrapped

        userRepository.insert(publicTenantId_1, user_1).asserting(_ shouldBe Right(user_1))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call UserDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- userRepository.insert(publicTenantId_1, user_1)

          _ = verifyZeroInteractions(userDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        userRepository
          .insert(publicTenantId_1, user_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call UserDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- userRepository.insert(publicTenantId_1, user_1).attempt

          _ = verifyZeroInteractions(userDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        userRepository.insert(publicTenantId_1, user_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "UserDb returns Left containing UserInsertionError" should {
      "return Left containing this error" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.insert(any[UserEntity.Write]) returns userInsertionErrorWrapped

        userRepository.insert(publicTenantId_1, user_1).asserting(_ shouldBe Left(userInsertionError))
      }
    }

    "UserDb returns exception" should {
      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.insert(any[UserEntity.Write]) returns testExceptionWrappedE[UserInsertionError]

        userRepository.insert(publicTenantId_1, user_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserRepository on delete" when {

    val deletedUserEntityReadWrapped = userEntityRead_1.asRight[UserNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UserDb" in {
        userDb.delete(any[TenantId], any[UserId]) returns deletedUserEntityReadWrapped

        for {
          _ <- userRepository.delete(publicTenantId_1, publicUserId_1)

          _ = verify(userDb).delete(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return Right containing deleted User" in {
        userDb.delete(any[TenantId], any[UserId]) returns deletedUserEntityReadWrapped

        userRepository
          .delete(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe Right(user_1))
      }
    }

    "UserDb returns Left containing UserNotFoundError" should {
      "return Left containing this error" in {
        val userNotFoundError = UserNotFoundError(publicTenantId_1, publicUserId_1)
        userDb.delete(any[TenantId], any[UserId]) returns userNotFoundError
          .asLeft[UserEntity.Read]
          .pure[doobie.ConnectionIO]

        userRepository
          .delete(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe Left(userNotFoundError))
      }
    }

    "UserDb returns exception" should {
      "return failed IO containing this exception" in {
        userDb
          .delete(any[TenantId], any[UserId]) returns testExceptionWrappedE[UserNotFoundError]

        userRepository
          .delete(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserRepository on getBy(:publicTenantId, :publicUserId)" when {

    "should always call UserDb" in {
      userDb.getByPublicUserId(any[TenantId], any[UserId]) returns Option(userEntityRead_1)
        .pure[doobie.ConnectionIO]

      for {
        _ <- userRepository.getBy(publicTenantId_1, publicUserId_1)

        _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
      } yield ()
    }

    "UserDb returns empty Option" should {
      "return empty Option" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns Option
          .empty[UserEntity.Read]
          .pure[doobie.ConnectionIO]

        userRepository.getBy(publicTenantId_1, publicUserId_1).asserting(_ shouldBe None)
      }
    }

    "UserDb returns Option containing UserEntity" should {
      "return Option containing User" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns Option(
          userEntityRead_1
        )
          .pure[doobie.ConnectionIO]

        userRepository.getBy(publicTenantId_1, publicUserId_1).asserting(_ shouldBe Some(user_1))
      }
    }

    "UserDb returns exception" should {
      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        userRepository
          .getBy(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserRepository on getAllForTenant" when {

    "should always call UserDb" in {
      userDb.getAllForTenant(any[TenantId]) returns Stream.empty

      for {
        _ <- userRepository.getAllForTenant(publicTenantId_1)

        _ = verify(userDb).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "UserDb returns empty Stream" should {
      "return empty List" in {
        userDb.getAllForTenant(any[TenantId]) returns Stream.empty

        userRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[User])
      }
    }

    "UserDb returns UserEntities in Stream" should {
      "return List containing Users" in {
        userDb.getAllForTenant(any[TenantId]) returns Stream(
          userEntityRead_1,
          userEntityRead_2,
          userEntityRead_3
        )

        userRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List(user_1, user_2, user_3))
      }
    }

    "UserDb returns exception" should {
      "return failed IO containing this exception" in {
        userDb.getAllForTenant(any[TenantId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        userRepository
          .getAllForTenant(publicTenantId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "UserRepository on getAllForTemplate" when {

    "should always call UserDb" in {
      userDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

      for {
        _ <- userRepository.getAllForTemplate(publicTenantId_1)

        _ = verify(userDb).getAllForTemplate(eqTo(publicTenantId_1))
      } yield ()
    }

    "UserDb returns empty Stream" should {
      "return empty List" in {
        userDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

        userRepository.getAllForTemplate(publicTenantId_1).asserting(_ shouldBe List.empty[User])
      }
    }

    "UserDb returns UserEntities in Stream" should {
      "return List containing Users" in {
        userDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream(
          userEntityRead_1,
          userEntityRead_2,
          userEntityRead_3
        )

        userRepository.getAllForTemplate(publicTenantId_1).asserting(_ shouldBe List(user_1, user_2, user_3))
      }
    }

    "UserDb returns exception" should {
      "return failed IO containing this exception" in {
        userDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        userRepository
          .getAllForTemplate(publicTenantId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
