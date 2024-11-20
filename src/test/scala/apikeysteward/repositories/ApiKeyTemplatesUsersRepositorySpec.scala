package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, publicUserId_3}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.{
  ApiKeyTemplatesUsersInsertionErrorImpl,
  ReferencedApiKeyTemplateDoesNotExistError,
  ReferencedUserDoesNotExistError
}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeyTemplatesUsersEntity, UserEntity}
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesUsersDb, UserDb}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxApplicativeErrorId, catsSyntaxApplicativeId, catsSyntaxEitherId, none}
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.sql.SQLException

class ApiKeyTemplatesUsersRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val apiKeyTemplateDb = mock[ApiKeyTemplateDb]
  private val userDb = mock[UserDb]
  private val apiKeyTemplatesUsersDb = mock[ApiKeyTemplatesUsersDb]

  private val apiKeyTemplatesUsersRepository =
    new ApiKeyTemplatesUsersRepository(apiKeyTemplateDb, userDb, apiKeyTemplatesUsersDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(apiKeyTemplateDb, userDb, apiKeyTemplatesUsersDb)

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, Int]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, Int]]

  private val inputPublicUserIds = List(publicUserId_1, publicUserId_2, publicUserId_3)

  "ApiKeyTemplatesUsersRepository on insertMany" when {

    "everything works correctly" should {

      "call ApiKeyTemplateDb, UserDb and ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns 3.asRight[ApiKeyTemplatesUsersInsertionError].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesUsersRepository.insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTemplateId_1))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_2))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_3))
          _ = verify(apiKeyTemplatesUsersDb).insertMany(eqTo(apiKeyTemplatesUsersEntitiesWrite))
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns 3.asRight[ApiKeyTemplatesUsersInsertionError].pure[doobie.ConnectionIO]

        val result = apiKeyTemplatesUsersRepository.insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    "ApiKeyTemplateDb returns empty Option" should {

      "NOT call UserDb or ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesUsersRepository.insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verifyZeroInteractions(userDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplatesUsersRepository
          .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "ApiKeyTemplateDb returns exception" should {

      "NOT call UserDb or ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyTemplatesUsersRepository
            .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
            .attempt

          _ = verifyZeroInteractions(userDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplatesUsersRepository
          .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UserDb returns empty Option for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          none[UserEntity.Read].pure[doobie.ConnectionIO],
          userEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository.insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          none[UserEntity.Read].pure[doobie.ConnectionIO],
          userEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(publicUserId_2, publicTenantId_1)))
      }
    }

    "UserDb returns exception for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]],
          userEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository
            .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
            .attempt

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]],
          userEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplatesUsersDb returns Left containing ApiKeyTemplatesUsersInsertionError" should {
      "return Left containing this error" in {
        val testSqlException = new SQLException("Test SQL Exception")
        val apiKeyTemplatesUsersInsertionError: ApiKeyTemplatesUsersInsertionError =
          ApiKeyTemplatesUsersInsertionErrorImpl(testSqlException)
        val apiKeyTemplatesUsersInsertionErrorWrapped =
          apiKeyTemplatesUsersInsertionError.asLeft[Int].pure[doobie.ConnectionIO]

        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersInsertionErrorWrapped

        apiKeyTemplatesUsersRepository
          .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .asserting(_ shouldBe Left(apiKeyTemplatesUsersInsertionError))
      }
    }

    "ApiKeyTemplatesUsersDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns testExceptionWrappedE[ApiKeyTemplatesUsersInsertionError]

        apiKeyTemplatesUsersRepository
          .insertMany(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
