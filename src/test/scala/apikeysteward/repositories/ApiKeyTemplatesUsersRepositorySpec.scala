package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{publicTemplateId_1, publicTemplateId_2, publicTemplateId_3}
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, publicUserId_3}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError.{
  ApiKeyTemplatesUsersInsertionErrorImpl,
  ReferencedApiKeyTemplateDoesNotExistError,
  ReferencedUserDoesNotExistError
}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.{
  ApiKeyTemplatesUsersInsertionError,
  ApiKeyTemplatesUsersNotFoundError
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

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, List[ApiKeyTemplatesUsersEntity.Read]]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, List[ApiKeyTemplatesUsersEntity.Read]]]

  private val inputPublicUserIds = List(publicUserId_1, publicUserId_2, publicUserId_3)
  private val inputPublicTemplateIds = List(publicTemplateId_1, publicTemplateId_2, publicTemplateId_3)

  "ApiKeyTemplatesUsersRepository on insertManyUsers" when {

    val apiKeyTemplatesUsersEntitiesReadWrapped =
      apiKeyTemplatesUsersEntitiesRead_sameTemplate
        .asRight[ApiKeyTemplatesUsersInsertionError]
        .pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApiKeyTemplateDb, UserDb and ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersEntitiesReadWrapped

        for {
          _ <- apiKeyTemplatesUsersRepository.insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_2))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_3))
          _ = verify(apiKeyTemplatesUsersDb).insertMany(eqTo(apiKeyTemplatesUsersEntitiesWrite_sameTemplate))
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersEntitiesReadWrapped

        val result =
          apiKeyTemplatesUsersRepository.insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    "ApiKeyTemplateDb returns empty Option" should {

      "NOT call UserDb or ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb
          .getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesUsersRepository.insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verifyZeroInteractions(userDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        apiKeyTemplateDb
          .getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplatesUsersRepository
          .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "ApiKeyTemplateDb returns exception" should {

      "NOT call UserDb or ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyTemplatesUsersRepository
            .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
            .attempt

          _ = verifyZeroInteractions(userDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplatesUsersRepository
          .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UserDb returns empty Option for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          none[UserEntity.Read].pure[doobie.ConnectionIO],
          userEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository.insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          none[UserEntity.Read].pure[doobie.ConnectionIO],
          userEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(publicUserId_2, publicTenantId_1)))
      }
    }

    "UserDb returns exception for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]],
          userEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository
            .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
            .attempt

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]],
          userEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
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
          apiKeyTemplatesUsersInsertionError.asLeft[List[ApiKeyTemplatesUsersEntity.Read]].pure[doobie.ConnectionIO]

        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersInsertionErrorWrapped

        apiKeyTemplatesUsersRepository
          .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .asserting(_ shouldBe Left(apiKeyTemplatesUsersInsertionError))
      }
    }

    "ApiKeyTemplatesUsersDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(
          any[TenantId],
          any[ApiKeyTemplateId]
        ) returns apiKeyTemplateEntityWrapped_1
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns (
          userEntityWrapped_1,
          userEntityWrapped_2,
          userEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns testExceptionWrappedE[ApiKeyTemplatesUsersInsertionError]

        apiKeyTemplatesUsersRepository
          .insertManyUsers(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplatesUsersRepository on insertManyTemplates" when {

    val apiKeyTemplatesUsersEntitiesReadWrapped =
      apiKeyTemplatesUsersEntitiesRead_sameUser.asRight[ApiKeyTemplatesUsersInsertionError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UserDb, ApiKeyTemplateDb and ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersEntitiesReadWrapped

        for {
          _ <- apiKeyTemplatesUsersRepository.insertManyTemplates(
            publicTenantId_1,
            publicUserId_1,
            inputPublicTemplateIds
          )

          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_2))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_3))
          _ = verify(apiKeyTemplatesUsersDb).insertMany(eqTo(apiKeyTemplatesUsersEntitiesWrite_sameUser))
        } yield ()
      }

      "return Right containing Unit value" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersEntitiesReadWrapped

        val result =
          apiKeyTemplatesUsersRepository.insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    "UserDb returns empty Option" should {

      "NOT call ApiKeyTemplateDb or ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesUsersRepository.insertManyTemplates(
            publicTenantId_1,
            publicUserId_1,
            inputPublicTemplateIds
          )

          _ = verifyZeroInteractions(apiKeyTemplateDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        apiKeyTemplatesUsersRepository
          .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1)))
      }
    }

    "UserDb returns exception" should {

      "NOT call ApiKeyTemplateDb or ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        for {
          _ <- apiKeyTemplatesUsersRepository
            .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
            .attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        apiKeyTemplatesUsersRepository
          .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateDb returns empty Option for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          none[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO],
          apiKeyTemplateEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository.insertManyTemplates(
            publicTenantId_1,
            publicUserId_1,
            inputPublicTemplateIds
          )

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          none[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO],
          apiKeyTemplateEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_2)))
      }
    }

    "ApiKeyTemplateDb returns exception for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]],
          apiKeyTemplateEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository
            .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
            .attempt

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]],
          apiKeyTemplateEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
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
          apiKeyTemplatesUsersInsertionError.asLeft[List[ApiKeyTemplatesUsersEntity.Read]].pure[doobie.ConnectionIO]

        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersInsertionErrorWrapped

        apiKeyTemplatesUsersRepository
          .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .asserting(_ shouldBe Left(apiKeyTemplatesUsersInsertionError))
      }
    }

    "ApiKeyTemplatesUsersDb returns exception" should {
      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.insertMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns testExceptionWrappedE[ApiKeyTemplatesUsersInsertionError]

        apiKeyTemplatesUsersRepository
          .insertManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplatesUsersRepository on deleteManyTemplates" when {

    val apiKeyTemplatesUsersEntitiesReadWrapped =
      apiKeyTemplatesUsersEntitiesRead_sameUser.asRight[ApiKeyTemplatesUsersNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UserDb, ApiKeyTemplateDb and ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.deleteMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersEntitiesReadWrapped

        for {
          _ <- apiKeyTemplatesUsersRepository.deleteManyTemplates(
            publicTenantId_1,
            publicUserId_1,
            inputPublicTemplateIds
          )

          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_2))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_3))
          _ = verify(apiKeyTemplatesUsersDb).deleteMany(eqTo(apiKeyTemplatesUsersEntitiesWrite_sameUser))
        } yield ()
      }

      "return Right containing Unit value" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.deleteMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersEntitiesReadWrapped

        val result =
          apiKeyTemplatesUsersRepository.deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    "UserDb returns empty Option" should {

      "NOT call ApiKeyTemplateDb or ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesUsersRepository.deleteManyTemplates(
            publicTenantId_1,
            publicUserId_1,
            inputPublicTemplateIds
          )

          _ = verifyZeroInteractions(apiKeyTemplateDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        apiKeyTemplatesUsersRepository
          .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1)))
      }
    }

    "UserDb returns exception" should {

      "NOT call ApiKeyTemplateDb or ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        for {
          _ <- apiKeyTemplatesUsersRepository
            .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
            .attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb, apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        apiKeyTemplatesUsersRepository
          .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateDb returns empty Option for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          none[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO],
          apiKeyTemplateEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository.deleteManyTemplates(
            publicTenantId_1,
            publicUserId_1,
            inputPublicTemplateIds
          )

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          none[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO],
          apiKeyTemplateEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_2)))
      }
    }

    "ApiKeyTemplateDb returns exception for one of the calls" should {

      "NOT call ApiKeyTemplatesUsersDb" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]],
          apiKeyTemplateEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesUsersRepository
            .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
            .attempt

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]],
          apiKeyTemplateEntityWrapped_3
        )

        apiKeyTemplatesUsersRepository
          .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplatesUsersDb returns Left containing ApiKeyTemplatesUsersInsertionError" should {
      "return Left containing this error" in {
        val apiKeyTemplatesUsersNotFoundError =
          ApiKeyTemplatesUsersNotFoundError(apiKeyTemplatesUsersEntitiesWrite_sameUser)
        val apiKeyTemplatesUsersNotFoundErrorWrapped =
          apiKeyTemplatesUsersNotFoundError.asLeft[List[ApiKeyTemplatesUsersEntity.Read]].pure[doobie.ConnectionIO]

        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.deleteMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns apiKeyTemplatesUsersNotFoundErrorWrapped

        apiKeyTemplatesUsersRepository
          .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .asserting(_ shouldBe Left(apiKeyTemplatesUsersNotFoundError))
      }
    }

    "ApiKeyTemplatesUsersDb returns exception" should {
      "return failed IO containing this exception" in {
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplateEntityWrapped_1,
          apiKeyTemplateEntityWrapped_2,
          apiKeyTemplateEntityWrapped_3
        )
        apiKeyTemplatesUsersDb.deleteMany(
          any[List[ApiKeyTemplatesUsersEntity.Write]]
        ) returns testExceptionWrappedE[ApiKeyTemplatesUsersNotFoundError]

        apiKeyTemplatesUsersRepository
          .deleteManyTemplates(publicTenantId_1, publicUserId_1, inputPublicTemplateIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
