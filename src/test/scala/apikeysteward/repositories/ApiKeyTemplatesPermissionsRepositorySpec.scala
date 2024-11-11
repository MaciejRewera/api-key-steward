package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.{
  ApiKeyTemplatesPermissionsInsertionError,
  ApiKeyTemplatesPermissionsNotFoundError
}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeyTemplatesPermissionsEntity, PermissionEntity}
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesPermissionsDb, PermissionDb}
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

class ApiKeyTemplatesPermissionsRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val apiKeyTemplateDb = mock[ApiKeyTemplateDb]
  private val permissionDb = mock[PermissionDb]
  private val apiKeyTemplatesPermissionsDb = mock[ApiKeyTemplatesPermissionsDb]

  private val apiKeyTemplatesPermissionsRepository =
    new ApiKeyTemplatesPermissionsRepository(apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)(
      noopTransactor
    )

  override def beforeEach(): Unit =
    reset(apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, List[ApiKeyTemplatesPermissionsEntity.Read]]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, List[ApiKeyTemplatesPermissionsEntity.Read]]]

  private val inputPublicPermissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)

  "ApiKeyTemplatesPermissionsRepository on insertMany" when {

    val apiKeyTemplatesPermissionsEntitiesReadWrapped =
      apiKeyTemplatesPermissionsEntitiesRead.asRight[ApiKeyTemplatesPermissionsInsertionError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApiKeyTemplateDb, PermissionDb and ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.insertMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns apiKeyTemplatesPermissionsEntitiesReadWrapped

        for {
          _ <- apiKeyTemplatesPermissionsRepository.insertMany(publicTemplateId_1, inputPublicPermissionIds)

          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTemplateId_1))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicPermissionId_1))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicPermissionId_2))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicPermissionId_3))
          _ = verify(apiKeyTemplatesPermissionsDb).insertMany(eqTo(apiKeyTemplatesPermissionsEntitiesWrite))
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.insertMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns apiKeyTemplatesPermissionsEntitiesReadWrapped

        val result = apiKeyTemplatesPermissionsRepository.insertMany(publicTemplateId_1, inputPublicPermissionIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    "ApiKeyTemplateDb returns empty Option" should {

      "NOT call PermissionDb or ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesPermissionsRepository.insertMany(publicTemplateId_1, inputPublicPermissionIds)

          _ = verifyZeroInteractions(permissionDb, apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplatesPermissionsRepository
          .insertMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "ApiKeyTemplateDb returns exception" should {

      "NOT call PermissionDb or ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyTemplatesPermissionsRepository.insertMany(publicTemplateId_1, inputPublicPermissionIds).attempt

          _ = verifyZeroInteractions(permissionDb, apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplatesPermissionsRepository
          .insertMany(publicTemplateId_1, inputPublicPermissionIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns empty Option for one of the calls" should {

      "NOT call ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          none[PermissionEntity.Read].pure[doobie.ConnectionIO],
          permissionEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesPermissionsRepository.insertMany(publicTemplateId_1, inputPublicPermissionIds)

          _ = verifyZeroInteractions(apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return Left containing ReferencedPermissionDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          none[PermissionEntity.Read].pure[doobie.ConnectionIO],
          permissionEntityWrapped_3
        )

        apiKeyTemplatesPermissionsRepository
          .insertMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Left(ReferencedPermissionDoesNotExistError(publicPermissionId_2)))
      }
    }

    "PermissionDb returns exception for one of the calls" should {

      "NOT call ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]],
          permissionEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesPermissionsRepository.insertMany(publicTemplateId_1, inputPublicPermissionIds).attempt

          _ = verifyZeroInteractions(apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]],
          permissionEntityWrapped_3
        )

        apiKeyTemplatesPermissionsRepository
          .insertMany(publicTemplateId_1, inputPublicPermissionIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns Left containing ApiKeyTemplatesPermissionsInsertionError" should {
      "return Left containing this error" in {
        val testSqlException = new SQLException("Test SQL Exception")
        val apiKeyTemplatesPermissionsInsertionError: ApiKeyTemplatesPermissionsInsertionError =
          ApiKeyTemplatesPermissionsInsertionErrorImpl(testSqlException)
        val apiKeyTemplatesPermissionsInsertionErrorWrapped =
          apiKeyTemplatesPermissionsInsertionError
            .asLeft[List[ApiKeyTemplatesPermissionsEntity.Read]]
            .pure[doobie.ConnectionIO]

        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.insertMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns apiKeyTemplatesPermissionsInsertionErrorWrapped

        apiKeyTemplatesPermissionsRepository
          .insertMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Left(apiKeyTemplatesPermissionsInsertionError))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.insertMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns testExceptionWrappedE[ApiKeyTemplatesPermissionsInsertionError]

        apiKeyTemplatesPermissionsRepository
          .insertMany(publicTemplateId_1, inputPublicPermissionIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplatesPermissionsRepository on deleteMany" when {

    val apiKeyTemplatesPermissionsEntitiesReadWrapped =
      apiKeyTemplatesPermissionsEntitiesRead.asRight[ApiKeyTemplatesPermissionsNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApiKeyTemplateDb, PermissionDb and ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.deleteMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns apiKeyTemplatesPermissionsEntitiesReadWrapped

        for {
          _ <- apiKeyTemplatesPermissionsRepository.deleteMany(publicTemplateId_1, inputPublicPermissionIds)

          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTemplateId_1))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicPermissionId_1))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicPermissionId_2))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicPermissionId_3))
          _ = verify(apiKeyTemplatesPermissionsDb).deleteMany(eqTo(apiKeyTemplatesPermissionsEntitiesWrite))
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.deleteMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns apiKeyTemplatesPermissionsEntitiesReadWrapped

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Right())
      }
    }

    "ApiKeyTemplateDb returns empty Option" should {

      "NOT call PermissionDb or ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplatesPermissionsRepository.deleteMany(publicTemplateId_1, inputPublicPermissionIds)

          _ = verifyZeroInteractions(permissionDb, apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "ApiKeyTemplateDb returns exception" should {

      "NOT call PermissionDb or ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyTemplatesPermissionsRepository.deleteMany(publicTemplateId_1, inputPublicPermissionIds).attempt

          _ = verifyZeroInteractions(permissionDb, apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns empty Option for one of the calls" should {

      "NOT call ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          none[PermissionEntity.Read].pure[doobie.ConnectionIO],
          permissionEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesPermissionsRepository.deleteMany(publicTemplateId_1, inputPublicPermissionIds)

          _ = verifyZeroInteractions(apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return Left containing ReferencedPermissionDoesNotExistError" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          none[PermissionEntity.Read].pure[doobie.ConnectionIO],
          permissionEntityWrapped_3
        )

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Left(ReferencedPermissionDoesNotExistError(publicPermissionId_2)))
      }
    }

    "PermissionDb returns exception for one of the calls" should {

      "NOT call ApiKeyTemplatesPermissionsDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]],
          permissionEntityWrapped_3
        )

        for {
          _ <- apiKeyTemplatesPermissionsRepository.deleteMany(publicTemplateId_1, inputPublicPermissionIds).attempt

          _ = verifyZeroInteractions(apiKeyTemplatesPermissionsDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]],
          permissionEntityWrapped_3
        )

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns Left containing ApiKeyTemplatesPermissionsNotFoundError" should {
      "return Left containing this error" in {
        val apiKeyTemplatesPermissionsNotFoundError =
          ApiKeyTemplatesPermissionsNotFoundError(apiKeyTemplatesPermissionsEntitiesWrite)
        val apiKeyTemplatesPermissionsNotFoundErrorWrapped =
          apiKeyTemplatesPermissionsNotFoundError
            .asLeft[List[ApiKeyTemplatesPermissionsEntity.Read]]
            .pure[doobie.ConnectionIO]

        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.deleteMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns apiKeyTemplatesPermissionsNotFoundErrorWrapped

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .asserting(_ shouldBe Left(apiKeyTemplatesPermissionsNotFoundError))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns apiKeyTemplateEntityWrapped
        permissionDb.getByPublicPermissionId(any[PermissionId]) returns (
          permissionEntityWrapped_1,
          permissionEntityWrapped_2,
          permissionEntityWrapped_3
        )
        apiKeyTemplatesPermissionsDb.deleteMany(
          any[List[ApiKeyTemplatesPermissionsEntity.Write]]
        ) returns testExceptionWrappedE[ApiKeyTemplatesPermissionsNotFoundError]

        apiKeyTemplatesPermissionsRepository
          .deleteMany(publicTemplateId_1, inputPublicPermissionIds)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplatesPermissionsRepository on getAllPermissionsForTemplate" when {

    "should always call ApiKeyTemplatesPermissionsDb" in {
      apiKeyTemplatesPermissionsDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

      for {
        _ <- apiKeyTemplatesPermissionsRepository.getAllPermissionsForTemplate(publicTemplateId_1)

        _ = verify(apiKeyTemplatesPermissionsDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
      } yield ()
    }

    "ApiKeyTemplatesPermissionsDb returns empty Stream" should {
      "return empty List" in {
        apiKeyTemplatesPermissionsDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

        apiKeyTemplatesPermissionsRepository
          .getAllPermissionsForTemplate(publicTemplateId_1)
          .asserting(_ shouldBe List.empty[Permission])
      }
    }

    "ApiKeyTemplatesPermissionsDb returns PermissionEntities in Stream" should {
      "return List containing Permissions" in {
        apiKeyTemplatesPermissionsDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )

        apiKeyTemplatesPermissionsRepository
          .getAllPermissionsForTemplate(publicTemplateId_1)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns
          Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyTemplatesPermissionsRepository
          .getAllPermissionsForTemplate(publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
