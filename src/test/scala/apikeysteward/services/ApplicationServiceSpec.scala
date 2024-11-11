package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.RepositoryErrors.ApplicationDbError
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError._
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationNotFoundError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{Application, ApplicationUpdate}
import apikeysteward.repositories.ApplicationRepository
import apikeysteward.routes.model.admin.application.{CreateApplicationRequest, UpdateApplicationRequest}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException
import java.util.UUID

class ApplicationServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val applicationRepository = mock[ApplicationRepository]

  private val applicationService = new ApplicationService(uuidGenerator, applicationRepository)

  override def beforeEach(): Unit =
    reset(uuidGenerator, applicationRepository)

  private val testException = new RuntimeException("Test Exception")

  "ApplicationService on createApplication" when {

    val createApplicationRequest =
      CreateApplicationRequest(
        name = applicationName_1,
        description = applicationDescription_1,
        permissions = List(createPermissionRequest_1, createPermissionRequest_2, createPermissionRequest_3)
      )

    val application = application_1.copy(permissions = List(permission_1, permission_2, permission_3))

    val applicationAlreadyExistsError = ApplicationAlreadyExistsError(publicApplicationIdStr_1)

    "everything works correctly" should {

      "call UuidGenerator and ApplicationRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        applicationRepository.insert(any[UUID], any[Application]) returns IO.pure(Right(application))

        for {
          _ <- applicationService.createApplication(publicTenantId_1, createApplicationRequest)

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(applicationRepository).insert(eqTo(publicTenantId_1), eqTo(application))
        } yield ()
      }

      "return the newly created Application returned by ApplicationRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        applicationRepository.insert(any[UUID], any[Application]) returns IO.pure(Right(application))

        applicationService
          .createApplication(publicTenantId_1, createApplicationRequest)
          .asserting(_ shouldBe Right(application))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call ApplicationRepository" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- applicationService.createApplication(publicTenantId_1, createApplicationRequest).attempt

          _ = verifyZeroInteractions(applicationRepository)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        applicationService
          .createApplication(publicTenantId_1, createApplicationRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApplicationRepository returns Left containing ApplicationAlreadyExistsError on the first try" should {

      "call UuidGenerator and ApplicationRepository again" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        val insertedApplication = application.copy(applicationId = publicApplicationId_2)
        applicationRepository.insert(any[UUID], any[Application]) returns (
          IO.pure(Left(applicationAlreadyExistsError)),
          IO.pure(Right(insertedApplication))
        )

        for {
          _ <- applicationService.createApplication(publicTenantId_1, createApplicationRequest)

          _ = verify(uuidGenerator, times(8)).generateUuid
          _ = verify(applicationRepository).insert(eqTo(publicTenantId_1), eqTo(application))
          _ = verify(applicationRepository).insert(eqTo(publicTenantId_1), eqTo(insertedApplication))
        } yield ()
      }

      "return the second created Application returned by ApplicationRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        val insertedApplication = application.copy(applicationId = publicApplicationId_2)
        applicationRepository.insert(any[UUID], any[Application]) returns (
          IO.pure(Left(applicationAlreadyExistsError)),
          IO.pure(Right(insertedApplication))
        )

        applicationService
          .createApplication(publicTenantId_1, createApplicationRequest)
          .asserting(_ shouldBe Right(insertedApplication))
      }
    }

    "ApplicationRepository keeps returning Left containing ApplicationAlreadyExistsError" should {

      "call UuidGenerator and ApplicationRepository again until reaching max retries amount" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_3),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_4),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
        )
        applicationRepository.insert(any[UUID], any[Application]) returns IO.pure(Left(applicationAlreadyExistsError))

        for {
          _ <- applicationService.createApplication(publicTenantId_1, createApplicationRequest)

          _ = verify(uuidGenerator, times(16)).generateUuid
          _ = verify(applicationRepository).insert(eqTo(publicTenantId_1), eqTo(application))
          _ = verify(applicationRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(application.copy(applicationId = publicApplicationId_2))
          )
          _ = verify(applicationRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(application.copy(applicationId = publicApplicationId_3))
          )
          _ = verify(applicationRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(application.copy(applicationId = publicApplicationId_4))
          )
        } yield ()
      }

      "return successful IO containing Left with ApplicationAlreadyExistsError" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_3),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicApplicationId_4),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
        )
        applicationRepository.insert(any[UUID], any[Application]) returns IO.pure(Left(applicationAlreadyExistsError))

        applicationService
          .createApplication(publicTenantId_1, createApplicationRequest)
          .asserting(_ shouldBe Left(applicationAlreadyExistsError))
      }
    }

    val testSqlException = new SQLException("Test SQL Exception")

    Seq(
      ReferencedTenantDoesNotExistError(publicTenantId_1),
      ApplicationInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"ApplicationRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "NOT call UuidGenerator or ApplicationRepository again" in {
          uuidGenerator.generateUuid returns (
            IO.pure(publicApplicationId_1),
            IO.pure(publicPermissionId_1),
            IO.pure(publicPermissionId_2),
            IO.pure(publicPermissionId_3)
          )
          applicationRepository.insert(any[UUID], any[Application]) returns IO.pure(
            Left(insertionError)
          )

          for {
            _ <- applicationService.createApplication(publicTenantId_1, createApplicationRequest)

            _ = verify(uuidGenerator, times(4)).generateUuid
            _ = verify(applicationRepository).insert(eqTo(publicTenantId_1), eqTo(application))
          } yield ()
        }

        "return failed IO containing this error" in {
          uuidGenerator.generateUuid returns (
            IO.pure(publicApplicationId_1),
            IO.pure(publicPermissionId_1),
            IO.pure(publicPermissionId_2),
            IO.pure(publicPermissionId_3)
          )
          applicationRepository.insert(any[UUID], any[Application]) returns IO.pure(
            Left(insertionError)
          )

          applicationService
            .createApplication(publicTenantId_1, createApplicationRequest)
            .asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApplicationRepository returns failed IO" should {

      "NOT call UuidGenerator or ApplicationRepository again" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        applicationRepository.insert(any[UUID], any[Application]) returns IO.raiseError(testException)

        for {
          _ <- applicationService.createApplication(publicTenantId_1, createApplicationRequest).attempt

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(applicationRepository).insert(eqTo(publicTenantId_1), eqTo(application))
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicApplicationId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        applicationRepository.insert(any[UUID], any[Application]) returns IO.raiseError(testException)

        applicationService
          .createApplication(publicTenantId_1, createApplicationRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationService on updateApplication" should {

    val updateApplicationRequest =
      UpdateApplicationRequest(name = applicationNameUpdated, description = applicationDescriptionUpdated)

    val updatedApplication =
      application_1.copy(name = applicationNameUpdated, description = applicationDescriptionUpdated)

    "call ApplicationRepository" in {
      applicationRepository.update(any[ApplicationUpdate]) returns IO.pure(Right(updatedApplication))

      for {
        _ <- applicationService.updateApplication(publicApplicationId_1, updateApplicationRequest)

        _ = verify(applicationRepository).update(eqTo(applicationUpdate_1))
      } yield ()
    }

    "return value returned by ApplicationRepository" when {

      "ApplicationRepository returns Right" in {
        applicationRepository.update(any[ApplicationUpdate]) returns IO.pure(Right(updatedApplication))

        applicationService
          .updateApplication(publicApplicationId_1, updateApplicationRequest)
          .asserting(_ shouldBe Right(updatedApplication))
      }

      "ApplicationRepository returns Left" in {
        applicationRepository.update(any[ApplicationUpdate]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        applicationService
          .updateApplication(publicApplicationId_1, updateApplicationRequest)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }
    }

    "return failed IO" when {
      "ApplicationRepository returns failed IO" in {
        applicationRepository.update(any[ApplicationUpdate]) returns IO.raiseError(testException)

        applicationService
          .updateApplication(publicApplicationId_1, updateApplicationRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationService on reactivateApplication" should {

    val reactivatedApplication = application_1.copy(isActive = true)

    "call ApplicationRepository" in {
      applicationRepository.activate(any[UUID]) returns IO.pure(Right(reactivatedApplication))

      for {
        _ <- applicationService.reactivateApplication(publicApplicationId_1)

        _ = verify(applicationRepository).activate(eqTo(publicApplicationId_1))
      } yield ()
    }

    "return value returned by ApplicationRepository" when {

      "ApplicationRepository returns Right" in {
        applicationRepository.activate(any[UUID]) returns IO.pure(Right(reactivatedApplication))

        applicationService
          .reactivateApplication(publicApplicationId_1)
          .asserting(_ shouldBe Right(reactivatedApplication))
      }

      "ApplicationRepository returns Left" in {
        applicationRepository.activate(any[UUID]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        applicationService
          .reactivateApplication(publicApplicationId_1)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }
    }

    "return failed IO" when {
      "ApplicationRepository returns failed IO" in {
        applicationRepository.activate(any[UUID]) returns IO.raiseError(testException)

        applicationService
          .reactivateApplication(publicApplicationId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationService on deactivateApplication" should {

    val deactivatedApplication = application_1.copy(isActive = false)

    "call ApplicationRepository" in {
      applicationRepository.deactivate(any[UUID]) returns IO.pure(Right(deactivatedApplication))

      for {
        _ <- applicationService.deactivateApplication(publicApplicationId_1)

        _ = verify(applicationRepository).deactivate(eqTo(publicApplicationId_1))
      } yield ()
    }

    "return value returned by ApplicationRepository" when {

      "ApplicationRepository returns Right" in {
        applicationRepository.deactivate(any[UUID]) returns IO.pure(Right(deactivatedApplication))

        applicationService
          .deactivateApplication(publicApplicationId_1)
          .asserting(_ shouldBe Right(deactivatedApplication))
      }

      "ApplicationRepository returns Left" in {
        applicationRepository.deactivate(any[UUID]) returns IO.pure(
          Left(ApplicationNotFoundError(publicApplicationIdStr_1))
        )

        applicationService
          .deactivateApplication(publicApplicationId_1)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }
    }

    "return failed IO" when {
      "ApplicationRepository returns failed IO" in {
        applicationRepository.deactivate(any[UUID]) returns IO.raiseError(testException)

        applicationService
          .deactivateApplication(publicApplicationId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationService on deleteApplication" should {

    val deletedApplication = application_1.copy(isActive = false)

    "call ApplicationRepository" in {
      applicationRepository.delete(any[UUID]) returns IO.pure(Right(deletedApplication))

      for {
        _ <- applicationService.deleteApplication(publicApplicationId_1)

        _ = verify(applicationRepository).delete(eqTo(publicApplicationId_1))
      } yield ()
    }

    "return value returned by ApplicationRepository" when {

      "ApplicationRepository returns Right" in {
        applicationRepository.delete(any[UUID]) returns IO.pure(Right(deletedApplication))

        applicationService.deleteApplication(publicApplicationId_1).asserting(_ shouldBe Right(deletedApplication))
      }

      "ApplicationRepository returns Left" in {
        applicationRepository.delete(any[UUID]) returns IO.pure(
          Left(ApplicationDbError.applicationNotFoundError(publicApplicationIdStr_1))
        )

        applicationService
          .deleteApplication(publicApplicationId_1)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }
    }

    "return failed IO" when {
      "ApplicationRepository returns failed IO" in {
        applicationRepository.delete(any[UUID]) returns IO.raiseError(testException)

        applicationService.deleteApplication(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationService on getBy(:applicationId)" should {

    "call ApplicationRepository" in {
      applicationRepository.getBy(any[ApplicationId]) returns IO.pure(Some(application_1))

      for {
        _ <- applicationService.getBy(publicApplicationId_1)

        _ = verify(applicationRepository).getBy(eqTo(publicApplicationId_1))
      } yield ()
    }

    "return the value returned by ApplicationRepository" when {

      "ApplicationRepository returns empty Option" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.pure(None)

        applicationService.getBy(publicApplicationId_1).asserting(_ shouldBe None)
      }

      "ApplicationRepository returns non-empty Option" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.pure(Some(application_1))

        applicationService.getBy(publicApplicationId_1).asserting(_ shouldBe Some(application_1))
      }
    }

    "return failed IO" when {
      "ApplicationRepository returns failed IO" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.raiseError(testException)

        applicationService.getBy(publicApplicationId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApplicationService on getAllForTenant" should {

    "call ApplicationRepository" in {
      applicationRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

      for {
        _ <- applicationService.getAllForTenant(publicTenantId_1)

        _ = verify(applicationRepository).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "return the value returned by ApplicationRepository" when {

      "ApplicationRepository returns empty List" in {
        applicationRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        applicationService.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[Application])
      }

      "ApplicationRepository returns non-empty List" in {
        applicationRepository.getAllForTenant(any[TenantId]) returns IO.pure(
          List(application_1, application_2, application_3)
        )

        applicationService
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(application_1, application_2, application_3))
      }
    }

    "return failed IO" when {
      "ApplicationRepository returns failed IO" in {
        applicationRepository.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        applicationService.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
