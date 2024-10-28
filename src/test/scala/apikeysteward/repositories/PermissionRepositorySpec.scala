package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData.{applicationEntityRead_1, publicApplicationId_1}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.db.entity.{ApplicationEntity, PermissionEntity}
import apikeysteward.repositories.db.{ApplicationDb, PermissionDb}
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

class PermissionRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val applicationDb = mock[ApplicationDb]
  private val permissionDb = mock[PermissionDb]

  private val permissionRepository = new PermissionRepository(applicationDb, permissionDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(applicationDb, permissionDb)

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, PermissionEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, PermissionEntity.Read]]

  "PermissionRepository on insert" when {

    val applicationId = 13L
    val applicationEntityReadWrapped =
      Option(applicationEntityRead_1.copy(id = applicationId)).pure[doobie.ConnectionIO]

    val permissionEntityReadWrapped = permissionEntityRead_1.asRight[PermissionInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")
    val permissionInsertionError: PermissionInsertionError = PermissionInsertionErrorImpl(testSqlException)
    val permissionInsertionErrorWrapped =
      permissionInsertionError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb and PermissionDb" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        for {
          _ <- permissionRepository.insert(publicApplicationId_1, permission_1)

          _ = verify(applicationDb).getByPublicApplicationId(eqTo(publicApplicationId_1))
          expectedPermissionEntityWrite = PermissionEntity.Write(
            applicationId = applicationId,
            publicPermissionId = publicPermissionIdStr_1,
            name = permissionName_1,
            description = permissionDescription_1
          )
          _ = verify(permissionDb).insert(eqTo(expectedPermissionEntityWrite))
        } yield ()
      }

      "return Right containing Permission" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        permissionRepository.insert(publicApplicationId_1, permission_1).asserting(_ shouldBe Right(permission_1))
      }
    }

    "ApplicationDb returns empty Option" should {

      "NOT call PermissionDb" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns none[ApplicationEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- permissionRepository.insert(publicApplicationId_1, permission_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing ReferencedApplicationDoesNotExistError" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns none[ApplicationEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .insert(publicApplicationId_1, permission_1)
          .asserting(_ shouldBe Left(ReferencedApplicationDoesNotExistError(publicApplicationId_1)))
      }
    }

    "ApplicationDb returns exception" should {

      "NOT call PermissionDb" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApplicationEntity.Read]]

        for {
          _ <- permissionRepository.insert(publicApplicationId_1, permission_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApplicationEntity.Read]]

        permissionRepository
          .insert(publicApplicationId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns Left containing PermissionInsertionError" should {
      "return Left containing this error" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionInsertionErrorWrapped

        permissionRepository
          .insert(publicApplicationId_1, permission_1)
          .asserting(_ shouldBe Left(permissionInsertionError))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns testExceptionWrappedE[PermissionInsertionError]

        permissionRepository
          .insert(publicApplicationId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on delete" when {

    val deletedPermissionEntityReadWrapped =
      permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call PermissionDb" in {
        permissionDb.delete(any[ApplicationId], any[PermissionId]) returns deletedPermissionEntityReadWrapped

        for {
          _ <- permissionRepository.delete(publicApplicationId_1, publicPermissionId_1)

          _ = verify(permissionDb).delete(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
        } yield ()
      }

      "return Right containing deleted Permission" in {
        permissionDb.delete(any[ApplicationId], any[PermissionId]) returns deletedPermissionEntityReadWrapped

        permissionRepository
          .delete(publicApplicationId_1, publicPermissionId_1)
          .asserting(_ shouldBe Right(permission_1))
      }
    }

    "PermissionDb returns Left containing PermissionNotFoundError" should {
      "return Left containing this error" in {
        val permissionNotFoundError = PermissionNotFoundError(publicApplicationId_1, publicPermissionId_1)
        permissionDb.delete(any[ApplicationId], any[PermissionId]) returns permissionNotFoundError
          .asLeft[PermissionEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .delete(publicApplicationId_1, publicPermissionId_1)
          .asserting(_ shouldBe Left(permissionNotFoundError))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb
          .delete(any[ApplicationId], any[PermissionId]) returns testExceptionWrappedE[PermissionNotFoundError]

        permissionRepository
          .delete(publicApplicationId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getBy(:publicApplicationId, :publicPermissionId)" when {

    "should always call PermissionDb" in {
      permissionDb.getByPublicPermissionId(any[ApplicationId], any[PermissionId]) returns Option(permissionEntityRead_1)
        .pure[doobie.ConnectionIO]

      for {
        _ <- permissionRepository.getBy(publicApplicationId_1, publicPermissionId_1)

        _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
      } yield ()
    }

    "PermissionDb returns empty Option" should {
      "return empty Option" in {
        permissionDb.getByPublicPermissionId(any[ApplicationId], any[PermissionId]) returns Option
          .empty[PermissionEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository.getBy(publicApplicationId_1, publicPermissionId_1).asserting(_ shouldBe None)
      }
    }

    "PermissionDb returns Option containing PermissionEntity" should {
      "return Option containing Permission" in {
        permissionDb.getByPublicPermissionId(any[ApplicationId], any[PermissionId]) returns Option(
          permissionEntityRead_1
        )
          .pure[doobie.ConnectionIO]

        permissionRepository.getBy(publicApplicationId_1, publicPermissionId_1).asserting(_ shouldBe Some(permission_1))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getByPublicPermissionId(any[ApplicationId], any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]]

        permissionRepository
          .getBy(publicApplicationId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getAllBy" when {

    val nameFragment = Some("test:name:fragment")

    "should always call PermissionDb" in {
      permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream.empty

      for {
        _ <- permissionRepository.getAllBy(publicApplicationId_1)(nameFragment)

        _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(nameFragment))
      } yield ()
    }

    "PermissionDb returns empty Stream" should {
      "return empty List" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream.empty

        permissionRepository.getAllBy(publicApplicationId_1)(nameFragment).asserting(_ shouldBe List.empty[Permission])
      }
    }

    "PermissionDb returns PermissionEntities in Stream" should {
      "return List containing Permissions" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )

        permissionRepository
          .getAllBy(publicApplicationId_1)(nameFragment)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream.raiseError[doobie.ConnectionIO](
          testException
        )

        permissionRepository
          .getAllBy(publicApplicationId_1)(nameFragment)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
