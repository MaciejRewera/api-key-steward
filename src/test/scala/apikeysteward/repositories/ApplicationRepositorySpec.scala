package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.Application
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError._
import apikeysteward.model.RepositoryErrors.ApplicationDbError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError.PermissionInsertionErrorImpl
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.{ApplicationEntity, PermissionEntity, TenantEntity}
import apikeysteward.repositories.db.{ApplicationDb, PermissionDb, TenantDb}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxApplicativeErrorId, catsSyntaxApplicativeId, catsSyntaxEitherId, none}
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.sql.SQLException
import java.util.UUID

class ApplicationRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val tenantDb = mock[TenantDb]
  private val applicationDb = mock[ApplicationDb]
  private val permissionDb = mock[PermissionDb]
  private val permissionRepository = mock[PermissionRepository]

  private val applicationRepository =
    new ApplicationRepository(tenantDb, applicationDb, permissionDb, permissionRepository)(noopTransactor)

  override def beforeEach(): Unit =
    reset(tenantDb, applicationDb, permissionDb, permissionRepository)

  private val applicationNotFoundError = ApplicationNotFoundError(publicApplicationIdStr_1)
  private val applicationNotFoundErrorWrapped =
    applicationNotFoundError.asLeft[ApplicationEntity.Read].pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, ApplicationEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, ApplicationEntity.Read]]

  "ApplicationRepository on insert" when {

    val tenantId = 13L
    val tenantEntityReadWrapped = Option(tenantEntityRead_1.copy(id = tenantId)).pure[doobie.ConnectionIO]

    val applicationEntityReadWrapped =
      applicationEntityRead_1.asRight[ApplicationInsertionError].pure[doobie.ConnectionIO]

    val permissionEntityReadWrapped = permissionEntityRead_1.asRight[PermissionInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")

    val applicationInsertionError: ApplicationInsertionError = ApplicationInsertionErrorImpl(testSqlException)
    val applicationInsertionErrorWrapped =
      applicationInsertionError.asLeft[ApplicationEntity.Read].pure[doobie.ConnectionIO]

    val permissionInsertionError: PermissionInsertionError = PermissionInsertionErrorImpl(testSqlException)
    val permissionInsertionErrorWrapped =
      permissionInsertionError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb, ApplicationDb and PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1)

          expectedApplicationEntityWrite = applicationEntityWrite_1.copy(tenantId = tenantId)
          _ = verify(applicationDb).insert(eqTo(expectedApplicationEntityWrite))
          _ = verify(permissionDb).insert(
            eqTo(permissionEntityWrite_1.copy(applicationId = applicationEntityRead_1.id))
          )
        } yield ()
      }

      "return Right containing Application" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        applicationRepository.insert(publicTenantId_1, application_1).asserting(_ shouldBe Right(application_1))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call either ApplicationDb or PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1)

          _ = verifyZeroInteractions(applicationDb, permissionDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        applicationRepository
          .insert(publicTenantId_1, application_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call either ApplicationDb or PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1).attempt

          _ = verifyZeroInteractions(applicationDb, permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        applicationRepository.insert(publicTenantId_1, application_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApplicationDb returns Left containing ApplicationInsertionError" should {

      "NOT call PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationInsertionErrorWrapped

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationInsertionErrorWrapped

        applicationRepository
          .insert(publicTenantId_1, application_1)
          .asserting(_ shouldBe Left(applicationInsertionError))
      }
    }

    "ApplicationDb returns different exception" should {

      "NOT call PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns testExceptionWrappedE[ApplicationInsertionError]

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns testExceptionWrappedE[ApplicationInsertionError]

        applicationRepository.insert(publicTenantId_1, application_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb.insert returns PermissionInsertionError" should {
      "return Left containing CannotInsertPermissionError" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionInsertionErrorWrapped

        applicationRepository
          .insert(publicTenantId_1, application_1)
          .asserting(_ shouldBe Left(CannotInsertPermissionError(publicApplicationId_1, permissionInsertionError)))
      }
    }

    "PermissionDb.insert returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[PermissionInsertionError, PermissionEntity.Read]]

        applicationRepository.insert(publicTenantId_1, application_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on update" when {

    val updatedApplicationEntityReadWrapped =
      applicationEntityRead_1
        .copy(name = applicationNameUpdated, description = applicationDescriptionUpdated)
        .asRight[ApplicationNotFoundError]
        .pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb and PermissionDb" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns updatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        for {
          _ <- applicationRepository.update(applicationUpdate_1)

          _ = verify(applicationDb).update(eqTo(applicationEntityUpdate_1))
          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
        } yield ()
      }

      "return Right containing updated Application" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns updatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        val expectedUpdatedApplication =
          application_1.copy(name = applicationNameUpdated, description = applicationDescriptionUpdated)

        applicationRepository.update(applicationUpdate_1).asserting(_ shouldBe Right(expectedUpdatedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {

      "NOT call PermissionDb" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns applicationNotFoundErrorWrapped

        for {
          _ <- applicationRepository.update(applicationUpdate_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns applicationNotFoundErrorWrapped

        applicationRepository.update(applicationUpdate_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns different exception" should {

      "NOT call PermissionDb" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns testExceptionWrappedE[ApplicationNotFoundError]

        for {
          _ <- applicationRepository.update(applicationUpdate_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns testExceptionWrappedE[ApplicationNotFoundError]

        applicationRepository.update(applicationUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns an exception" should {
      "return failed IO containing this exception" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns updatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        applicationRepository.update(applicationUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on activate" when {

    val activatedApplicationEntityRead = applicationEntityRead_1.copy(deactivatedAt = None)
    val activatedApplicationEntityReadWrapped =
      activatedApplicationEntityRead.asRight[ApplicationNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb and PermissionDb" in {
        applicationDb.activate(any[UUID]) returns activatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        for {
          _ <- applicationRepository.activate(publicApplicationId_1)

          _ = verify(applicationDb).activate(eqTo(publicApplicationId_1))
          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
        } yield ()
      }

      "return Right containing activated Application" in {
        applicationDb.activate(any[UUID]) returns activatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        val expectedActivatedApplication = application_1.copy(isActive = true)

        applicationRepository.activate(publicApplicationId_1).asserting(_ shouldBe Right(expectedActivatedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {

      "NOT call PermissionDb" in {
        applicationDb.activate(any[UUID]) returns applicationNotFoundErrorWrapped

        for {
          _ <- applicationRepository.activate(publicApplicationId_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        applicationDb.activate(any[UUID]) returns applicationNotFoundErrorWrapped

        applicationRepository.activate(publicApplicationId_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns different exception" should {

      "NOT call PermissionDb" in {
        applicationDb.activate(any[UUID]) returns testExceptionWrappedE[ApplicationNotFoundError]

        for {
          _ <- applicationRepository.activate(publicApplicationId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        applicationDb.activate(any[UUID]) returns testExceptionWrappedE[ApplicationNotFoundError]

        applicationRepository.activate(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns an exception" should {
      "return failed IO containing this exception" in {
        applicationDb.activate(any[UUID]) returns activatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        applicationRepository.activate(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on deactivate" when {

    val deactivatedApplicationEntityRead = applicationEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deactivatedApplicationEntityReadWrapped =
      deactivatedApplicationEntityRead.asRight[ApplicationNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb and PermissionDb" in {
        applicationDb.deactivate(any[UUID]) returns deactivatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        for {
          _ <- applicationRepository.deactivate(publicApplicationId_1)

          _ = verify(applicationDb).deactivate(eqTo(publicApplicationId_1))
          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
        } yield ()
      }

      "return Right containing deactivated Application" in {
        applicationDb.deactivate(any[UUID]) returns deactivatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        val expectedDeactivatedApplication = application_1.copy(isActive = false)

        applicationRepository
          .deactivate(publicApplicationId_1)
          .asserting(_ shouldBe Right(expectedDeactivatedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {

      "NOT call PermissionDb" in {
        applicationDb.deactivate(any[UUID]) returns applicationNotFoundErrorWrapped

        for {
          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        applicationDb.deactivate(any[UUID]) returns applicationNotFoundErrorWrapped

        applicationRepository.deactivate(publicApplicationId_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns different exception" should {

      "NOT call PermissionDb" in {
        applicationDb.deactivate(any[UUID]) returns testExceptionWrappedE[ApplicationNotFoundError]

        for {
          _ <- applicationRepository.deactivate(publicApplicationId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        applicationDb.deactivate(any[UUID]) returns testExceptionWrappedE[ApplicationNotFoundError]

        applicationRepository.deactivate(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns an exception" should {
      "return failed IO containing this exception" in {
        applicationDb.deactivate(any[UUID]) returns deactivatedApplicationEntityReadWrapped
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        applicationRepository.deactivate(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on delete" when {

    val deletedApplicationEntityRead = applicationEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deletedApplicationEntityReadWrapped =
      deletedApplicationEntityRead.asRight[ApplicationDbError].pure[doobie.ConnectionIO]

    val applicationNotFound = applicationNotFoundError.asInstanceOf[ApplicationDbError]
    val applicationNotFoundWrapped = applicationNotFound.asLeft[ApplicationEntity.Read].pure[doobie.ConnectionIO]

    val applicationIsNotDeactivatedError: ApplicationDbError = ApplicationIsNotDeactivatedError(publicApplicationId_1)
    val applicationIsNotDeactivatedErrorWrapped =
      applicationIsNotDeactivatedError.asLeft[ApplicationEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb and PermissionDb" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_2.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_3.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO]
        )
        applicationDb.deleteDeactivated(any[ApplicationId]) returns deletedApplicationEntityReadWrapped

        for {
          _ <- applicationRepository.delete(publicApplicationId_1)

          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
          _ = verify(permissionRepository).deleteOp(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
          _ = verify(permissionRepository).deleteOp(eqTo(publicApplicationId_1), eqTo(publicPermissionId_2))
          _ = verify(permissionRepository).deleteOp(eqTo(publicApplicationId_1), eqTo(publicPermissionId_3))
          _ = verify(applicationDb).deleteDeactivated(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return Right containing deleted Application" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_2.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_3.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO]
        )
        applicationDb.deleteDeactivated(any[ApplicationId]) returns deletedApplicationEntityReadWrapped

        val expectedDeletedApplication =
          application_1.copy(isActive = false, permissions = List(permission_1, permission_2, permission_3))

        applicationRepository.delete(publicApplicationId_1).asserting(_ shouldBe Right(expectedDeletedApplication))
      }
    }

    "PermissionDb.getAllBy returns an exception" should {

      "NOT call either PermissionDb.delete or ApplicationDb" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- applicationRepository.delete(publicApplicationId_1).attempt

          _ = verify(permissionDb, times(0)).delete(any[ApplicationId], any[PermissionId])
          _ = verifyZeroInteractions(applicationDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1) ++
          Stream.raiseError[doobie.ConnectionIO](testException)

        applicationRepository.delete(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionRepository.deleteOp returns PermissionNotFoundError" should {

      val permissionNotFoundError = PermissionNotFoundError(publicApplicationId_1, publicPermissionId_1)

      "NOT call ApplicationDb" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionNotFoundError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]
        )

        for {
          _ <- applicationRepository.delete(publicApplicationId_1)
          _ = verifyZeroInteractions(applicationDb)
        } yield ()
      }

      "return Left containing CannotDeletePermissionError" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionNotFoundError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]
        )

        applicationRepository
          .delete(publicApplicationId_1)
          .asserting(_ shouldBe Left(CannotDeletePermissionError(publicApplicationId_1, permissionNotFoundError)))
      }
    }

    "PermissionRepository.deleteOp returns different exception" should {

      "NOT call ApplicationDb" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[PermissionNotFoundError, PermissionEntity.Read]]
        )

        for {
          _ <- applicationRepository.delete(publicApplicationId_1).attempt
          _ = verifyZeroInteractions(applicationDb)
        } yield ()
      }

      "return Left containing CannotDeletePermissionError" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[PermissionNotFoundError, PermissionEntity.Read]]
        )

        applicationRepository
          .delete(publicApplicationId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {
      "return Left containing this error" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns permissionEntityRead_1
          .asRight[PermissionNotFoundError]
          .pure[doobie.ConnectionIO]
        applicationDb.deleteDeactivated(any[ApplicationId]) returns applicationNotFoundWrapped

        applicationRepository.delete(publicApplicationId_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotDeactivatedError" should {
      "return Left containing this error" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns permissionEntityRead_1
          .asRight[PermissionNotFoundError]
          .pure[doobie.ConnectionIO]
        applicationDb.deleteDeactivated(any[ApplicationId]) returns applicationIsNotDeactivatedErrorWrapped

        applicationRepository.delete(publicApplicationId_1).asserting(_ shouldBe Left(applicationIsNotDeactivatedError))
      }
    }

    "ApplicationDb returns different exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)
        permissionRepository.deleteOp(any[ApplicationId], any[PermissionId]) returns permissionEntityRead_1
          .asRight[PermissionNotFoundError]
          .pure[doobie.ConnectionIO]
        applicationDb.deleteDeactivated(any[ApplicationId]) returns testExceptionWrappedE[ApplicationDbError]

        applicationRepository.delete(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on getBy(:applicationId)" when {

    "should always call ApplicationDb" in {
      applicationDb.getByPublicApplicationId(any[ApplicationId]) returns Option(applicationEntityRead_1)
        .pure[doobie.ConnectionIO]
      permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream.empty

      for {
        _ <- applicationRepository.getBy(publicApplicationId_1)

        _ = verify(applicationDb).getByPublicApplicationId(eqTo(publicApplicationId_1))
      } yield ()
    }

    "ApplicationDb returns empty Option" should {

      "NOT call PermissionDb" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns none[ApplicationEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- applicationRepository.getBy(publicApplicationId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty Option" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns none[ApplicationEntity.Read]
          .pure[doobie.ConnectionIO]

        applicationRepository.getBy(publicApplicationId_1).asserting(_ shouldBe None)
      }
    }

    "ApplicationDb returns Option containing ApplicationEntity" should {

      "call PermissionDb with publicApplicationId" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns Option(applicationEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        for {
          _ <- applicationRepository.getBy(publicApplicationId_1)
          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
        } yield ()
      }

      "return Option containing Application" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns Option(applicationEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1)

        applicationRepository.getBy(publicApplicationId_1).asserting(_ shouldBe Some(application_1))
      }
    }

    "ApplicationDb returns exception" should {

      "NOT call PermissionDb" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApplicationEntity.Read]]

        for {
          _ <- applicationRepository.getBy(publicApplicationId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApplicationEntity.Read]]

        applicationRepository.getBy(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        applicationDb.getByPublicApplicationId(any[ApplicationId]) returns Option(applicationEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        applicationRepository.getBy(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on getAllForTenant" when {

    "should always call ApplicationDb" in {
      applicationDb.getAllForTenant(any[TenantId]) returns Stream.empty

      for {
        _ <- applicationRepository.getAllForTenant(publicTenantId_1)

        _ = verify(applicationDb).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "ApplicationDb returns empty Stream" should {

      "NOT call PermissionDb" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream.empty

        for {
          _ <- applicationRepository.getAllForTenant(publicTenantId_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty List" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream.empty

        applicationRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[Application])
      }
    }

    "ApplicationDb returns ApplicationEntities in Stream" should {

      "call PermissionDb with every publicApplicationId" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream(
          applicationEntityRead_1,
          applicationEntityRead_2,
          applicationEntityRead_3
        )
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        for {
          _ <- applicationRepository.getAllForTenant(publicTenantId_1)

          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_1))(eqTo(none[String]))
          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_2))(eqTo(none[String]))
          _ = verify(permissionDb).getAllBy(eqTo(publicApplicationId_3))(eqTo(none[String]))
        } yield ()
      }

      "return List containing Applications" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream(
          applicationEntityRead_1,
          applicationEntityRead_2,
          applicationEntityRead_3
        )
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        applicationRepository
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(application_1, application_2, application_3))
      }
    }

    "ApplicationDb returns exception" should {

      "NOT call PermissionDb" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- applicationRepository.getAllForTenant(publicTenantId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        applicationRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        applicationDb.getAllForTenant(any[TenantId]) returns Stream(applicationEntityRead_1)
        permissionDb.getAllBy(any[ApplicationId])(any[Option[String]]) returns Stream(permissionEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        applicationRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
