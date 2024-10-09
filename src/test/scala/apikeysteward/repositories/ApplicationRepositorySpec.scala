package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.Application
import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError._
import apikeysteward.model.RepositoryErrors.ApplicationDbError._
import apikeysteward.repositories.db.entity.{ApplicationEntity, TenantEntity}
import apikeysteward.repositories.db.{ApplicationDb, TenantDb}
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

  private val applicationRepository = new ApplicationRepository(tenantDb, applicationDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(tenantDb, applicationDb)

  private val applicationNotFoundError = ApplicationNotFoundError(publicApplicationIdStr_1)
  private val applicationNotFoundErrorWrapped =
    applicationNotFoundError.asLeft[ApplicationEntity.Read].pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")
  private val testSqlException = new SQLException("Test SQL Exception")

  private val testExceptionWrappedOpt: doobie.ConnectionIO[Option[ApplicationEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Option[ApplicationEntity.Read]]

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, ApplicationEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, ApplicationEntity.Read]]

  "ApplicationRepository on insert" when {

    val tenantId = 13L
    val tenantEntityReadWrapped = Option(tenantEntityRead_1.copy(id = tenantId)).pure[doobie.ConnectionIO]

    val applicationEntityReadWrapped =
      applicationEntityRead_1.asRight[ApplicationInsertionError].pure[doobie.ConnectionIO]

    val applicationInsertionError: ApplicationInsertionError = ApplicationInsertionErrorImpl(testSqlException)
    val applicationInsertionErrorWrapped =
      applicationInsertionError.asLeft[ApplicationEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb and ApplicationDb" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationEntityReadWrapped

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1)

          expectedApplicationEntityWrite = ApplicationEntity.Write(
            tenantId = tenantId,
            publicApplicationId = publicApplicationIdStr_1,
            name = applicationName_1,
            description = applicationDescription_1
          )
          _ = verify(applicationDb).insert(eqTo(expectedApplicationEntityWrite))
        } yield ()
      }

      "return Right containing Application" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationEntityReadWrapped

        applicationRepository.insert(publicTenantId_1, application_1).asserting(_ shouldBe Right(application_1))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call ApplicationDb" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1)

          _ = verifyZeroInteractions(applicationDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        applicationRepository
          .insert(publicTenantId_1, application_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns different exception" should {

      "NOT call ApplicationDb" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- applicationRepository.insert(publicTenantId_1, application_1).attempt

          _ = verifyZeroInteractions(applicationDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        applicationRepository.insert(publicTenantId_1, application_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApplicationDb returns Left containing ApplicationInsertionError" should {
      "return Left containing this error" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns applicationInsertionErrorWrapped

        applicationRepository
          .insert(publicTenantId_1, application_1)
          .asserting(_ shouldBe Left(applicationInsertionError))
      }
    }

    "ApplicationDb returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns tenantEntityReadWrapped
        applicationDb.insert(any[ApplicationEntity.Write]) returns testExceptionWrappedE[ApplicationInsertionError]

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

      "call ApplicationDb" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns updatedApplicationEntityReadWrapped

        for {
          _ <- applicationRepository.update(applicationUpdate_1)

          expectedApplicationEntityUpdate = ApplicationEntity.Update(
            publicApplicationId = publicApplicationIdStr_1,
            name = applicationNameUpdated,
            description = applicationDescriptionUpdated
          )
          _ = verify(applicationDb).update(eqTo(expectedApplicationEntityUpdate))
        } yield ()
      }

      "return Right containing updated Application" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns updatedApplicationEntityReadWrapped
        val expectedUpdatedApplication =
          application_1.copy(name = applicationNameUpdated, description = applicationDescriptionUpdated)

        applicationRepository.update(applicationUpdate_1).asserting(_ shouldBe Right(expectedUpdatedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {
      "return Left containing this error" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns applicationNotFoundErrorWrapped

        applicationRepository.update(applicationUpdate_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns different exception" should {
      "return failed IO containing this exception" in {
        applicationDb.update(any[ApplicationEntity.Update]) returns testExceptionWrappedE[ApplicationNotFoundError]

        applicationRepository.update(applicationUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on activate" when {

    val activatedApplicationEntityRead = applicationEntityRead_1.copy(deactivatedAt = None)
    val activatedApplicationEntityReadWrapped =
      activatedApplicationEntityRead.asRight[ApplicationNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb" in {
        applicationDb.activate(any[UUID]) returns activatedApplicationEntityReadWrapped

        for {
          _ <- applicationRepository.activate(publicApplicationId_1)

          _ = verify(applicationDb).activate(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return Right containing activated Application" in {
        applicationDb.activate(any[UUID]) returns activatedApplicationEntityReadWrapped
        val expectedActivatedApplication = application_1.copy(isActive = true)

        applicationRepository.activate(publicApplicationId_1).asserting(_ shouldBe Right(expectedActivatedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {
      "return Left containing this error" in {
        applicationDb.activate(any[UUID]) returns applicationNotFoundErrorWrapped

        applicationRepository.activate(publicApplicationId_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns different exception" should {
      "return failed IO containing this exception" in {
        applicationDb.activate(any[UUID]) returns testExceptionWrappedE[ApplicationNotFoundError]

        applicationRepository.activate(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on deactivate" when {

    val deactivatedApplicationEntityRead = applicationEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deactivatedApplicationEntityReadWrapped =
      deactivatedApplicationEntityRead.asRight[ApplicationNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApplicationDb" in {
        applicationDb.deactivate(any[UUID]) returns deactivatedApplicationEntityReadWrapped

        for {
          _ <- applicationRepository.deactivate(publicApplicationId_1)

          _ = verify(applicationDb).deactivate(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return Right containing deactivated Application" in {
        applicationDb.deactivate(any[UUID]) returns deactivatedApplicationEntityReadWrapped
        val expectedDeactivatedApplication = application_1.copy(isActive = false)

        applicationRepository
          .deactivate(publicApplicationId_1)
          .asserting(_ shouldBe Right(expectedDeactivatedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {
      "return Left containing this error" in {
        applicationDb.deactivate(any[UUID]) returns applicationNotFoundErrorWrapped

        applicationRepository.deactivate(publicApplicationId_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns different exception" should {
      "return failed IO containing this exception" in {
        applicationDb.deactivate(any[UUID]) returns testExceptionWrappedE[ApplicationNotFoundError]

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

      "call ApplicationDb" in {
        applicationDb.deleteDeactivated(any[UUID]) returns deletedApplicationEntityReadWrapped

        for {
          _ <- applicationRepository.delete(publicApplicationId_1)

          _ = verify(applicationDb).deleteDeactivated(eqTo(publicApplicationId_1))
        } yield ()
      }

      "return Right containing deleted Application" in {
        applicationDb.deleteDeactivated(any[UUID]) returns deletedApplicationEntityReadWrapped
        val expectedDeletedApplication = application_1.copy(isActive = false)

        applicationRepository.delete(publicApplicationId_1).asserting(_ shouldBe Right(expectedDeletedApplication))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotFoundError" should {
      "return Left containing this error" in {
        applicationDb.deleteDeactivated(any[UUID]) returns applicationNotFoundWrapped

        applicationRepository.delete(publicApplicationId_1).asserting(_ shouldBe Left(applicationNotFoundError))
      }
    }

    "ApplicationDb returns Left containing ApplicationNotDeactivatedError" should {
      "return Left containing this error" in {
        applicationDb.deleteDeactivated(any[UUID]) returns applicationIsNotDeactivatedErrorWrapped

        applicationRepository.delete(publicApplicationId_1).asserting(_ shouldBe Left(applicationIsNotDeactivatedError))
      }
    }

    "ApplicationDb returns different exception" should {
      "return failed IO containing this exception" in {
        applicationDb.deleteDeactivated(any[UUID]) returns testExceptionWrappedE[ApplicationDbError]

        applicationRepository.delete(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on getBy(:applicationId)" when {

    "should always call ApplicationDb" in {
      applicationDb.getByPublicApplicationId(any[UUID]) returns Option(applicationEntityRead_1)
        .pure[doobie.ConnectionIO]

      for {
        _ <- applicationRepository.getBy(publicApplicationId_1)

        _ = verify(applicationDb).getByPublicApplicationId(eqTo(publicApplicationId_1))
      } yield ()
    }

    "ApplicationDb returns empty Option" should {
      "return empty Option" in {
        applicationDb
          .getByPublicApplicationId(any[UUID]) returns Option.empty[ApplicationEntity.Read].pure[doobie.ConnectionIO]

        applicationRepository.getBy(publicApplicationId_1).asserting(_ shouldBe None)
      }
    }

    "ApplicationDb returns Option containing ApplicationEntity" should {
      "return Option containing Application" in {
        applicationDb.getByPublicApplicationId(any[UUID]) returns Option(applicationEntityRead_1)
          .pure[doobie.ConnectionIO]

        applicationRepository.getBy(publicApplicationId_1).asserting(_ shouldBe Some(application_1))
      }
    }

    "ApplicationDb returns exception" should {
      "return failed IO containing this exception" in {
        applicationDb.getByPublicApplicationId(any[UUID]) returns testExceptionWrappedOpt

        applicationRepository.getBy(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationRepository on getAllForTenant" when {

    "should always call ApplicationDb" in {
      applicationDb.getAllForTenant(any[UUID]) returns Stream.empty

      for {
        _ <- applicationRepository.getAllForTenant(publicTenantId_1)

        _ = verify(applicationDb).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "ApplicationDb returns empty Stream" should {
      "return empty List" in {
        applicationDb.getAllForTenant(any[UUID]) returns Stream.empty

        applicationRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[Application])
      }
    }

    "ApplicationDb returns ApplicationEntities in Stream" should {
      "return List containing Applications" in {
        applicationDb.getAllForTenant(any[UUID]) returns Stream(
          applicationEntityRead_1,
          applicationEntityRead_2,
          applicationEntityRead_3
        )

        applicationRepository
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(application_1, application_2, application_3))
      }
    }

    "ApplicationDb returns exception" should {
      "return failed IO containing this exception" in {
        applicationDb.getAllForTenant(any[UUID]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        applicationRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

  }

}
