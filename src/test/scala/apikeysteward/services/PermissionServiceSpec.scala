package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData.{application_1, publicApplicationIdStr_1, publicApplicationId_1}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationNotFoundError
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionNotFoundError
import apikeysteward.model.{Application, Permission}
import apikeysteward.repositories.{ApplicationRepository, PermissionRepository}
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException
import java.util.UUID

class PermissionServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val permissionRepository = mock[PermissionRepository]
  private val applicationRepository = mock[ApplicationRepository]

  private val permissionService = new PermissionService(uuidGenerator, permissionRepository, applicationRepository)

  override def beforeEach(): Unit =
    reset(uuidGenerator, permissionRepository)

  private val testException = new RuntimeException("Test Exception")
  private val testSqlException = new SQLException("Test SQL Exception")

  "PermissionService on createPermission" when {

    val createPermissionRequest =
      CreatePermissionRequest(name = permissionName_1, description = permissionDescription_1)

    val permissionAlreadyExistsError = PermissionAlreadyExistsError(publicPermissionIdStr_1)

    "everything works correctly" should {

      "call UuidGenerator and PermissionRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[ApplicationId], any[Permission]) returns IO.pure(Right(permission_1))

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

          _ = verify(uuidGenerator).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
        } yield ()
      }

      "return the newly created Permission returned by PermissionRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[ApplicationId], any[Permission]) returns IO.pure(Right(permission_1))

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .asserting(_ shouldBe Right(permission_1))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call PermissionRepository" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest).attempt

          _ = verifyZeroInteractions(permissionRepository)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionRepository returns Left containing PermissionAlreadyExistsError on the first try" should {

      "call UuidGenerator and PermissionRepository again" in {
        uuidGenerator.generateUuid returns (IO.pure(publicPermissionId_1), IO.pure(publicPermissionId_2))
        val insertedPermission = permission_1.copy(permissionId = publicPermissionId_2)
        permissionRepository.insert(any[UUID], any[Permission]) returns (
          IO.pure(Left(permissionAlreadyExistsError)),
          IO.pure(Right(insertedPermission))
        )

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

          _ = verify(uuidGenerator, times(2)).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(insertedPermission))
        } yield ()
      }

      "return the second created Permission returned by PermissionRepository" in {
        uuidGenerator.generateUuid returns (IO.pure(publicPermissionId_1), IO.pure(publicPermissionId_2))
        val insertedPermission = permission_1.copy(permissionId = publicPermissionId_2)
        permissionRepository.insert(any[UUID], any[Permission]) returns (
          IO.pure(Left(permissionAlreadyExistsError)),
          IO.pure(Right(insertedPermission))
        )

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .asserting(_ shouldBe Right(insertedPermission))
      }
    }

    "PermissionRepository keeps returning Left containing PermissionAlreadyExistsError" should {

      "call UuidGenerator and PermissionRepository again until reaching max retries amount" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicPermissionId_4)
        )
        permissionRepository.insert(any[UUID], any[Permission]) returns IO.pure(Left(permissionAlreadyExistsError))

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
          _ = verify(permissionRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(permission_1.copy(permissionId = publicPermissionId_2))
          )
          _ = verify(permissionRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(permission_1.copy(permissionId = publicPermissionId_3))
          )
          _ = verify(permissionRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(permission_1.copy(permissionId = publicPermissionId_4))
          )
        } yield ()
      }

      "return successful IO containing Left with PermissionAlreadyExistsError" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicPermissionId_4)
        )
        permissionRepository.insert(any[UUID], any[Permission]) returns IO.pure(Left(permissionAlreadyExistsError))

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .asserting(_ shouldBe Left(permissionAlreadyExistsError))
      }
    }

    val applicationId = 13L
    Seq(
      PermissionAlreadyExistsForThisApplicationError(permissionName_1, applicationId),
      ReferencedApplicationDoesNotExistError(publicTenantId_1),
      PermissionInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"PermissionRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        val referencedApplicationDoesNotExistError: ReferencedApplicationDoesNotExistError =
          ReferencedApplicationDoesNotExistError(publicTenantId_1)

        "NOT call UuidGenerator or PermissionRepository again" in {
          uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
          permissionRepository.insert(any[ApplicationId], any[Permission]) returns IO.pure(
            Left(referencedApplicationDoesNotExistError)
          )

          for {
            _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

            _ = verify(uuidGenerator).generateUuid
            _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
          } yield ()
        }

        "return failed IO containing this error" in {
          uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
          permissionRepository.insert(any[ApplicationId], any[Permission]) returns IO.pure(
            Left(referencedApplicationDoesNotExistError)
          )

          permissionService
            .createPermission(publicTenantId_1, createPermissionRequest)
            .asserting(_ shouldBe Left(referencedApplicationDoesNotExistError))
        }
      }
    }

    "PermissionRepository returns failed IO" should {

      "NOT call UuidGenerator or PermissionRepository again" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[UUID], any[Permission]) returns IO.raiseError(testException)

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest).attempt

          _ = verify(uuidGenerator).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[UUID], any[Permission]) returns IO.raiseError(testException)

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on deletePermission" should {

    "call PermissionRepository" in {
      permissionRepository.delete(any[ApplicationId], any[PermissionId]) returns IO.pure(Right(permission_1))

      for {
        _ <- permissionService.deletePermission(publicApplicationId_1, publicPermissionId_1)

        _ = verify(permissionRepository).delete(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
      } yield ()
    }

    "return value returned by PermissionRepository" when {

      "PermissionRepository returns Right" in {
        permissionRepository.delete(any[ApplicationId], any[PermissionId]) returns IO.pure(Right(permission_1))

        permissionService
          .deletePermission(publicApplicationId_1, publicPermissionId_1)
          .asserting(_ shouldBe Right(permission_1))
      }

      "PermissionRepository returns Left" in {
        permissionRepository.delete(any[ApplicationId], any[PermissionId]) returns IO.pure(
          Left(PermissionNotFoundError(publicApplicationId_1, publicPermissionId_1))
        )

        permissionService
          .deletePermission(publicApplicationId_1, publicPermissionId_1)
          .asserting(_ shouldBe Left(PermissionNotFoundError(publicApplicationId_1, publicPermissionId_1)))
      }
    }

    "return failed IO" when {
      "PermissionRepository returns failed IO" in {
        permissionRepository.delete(any[ApplicationId], any[PermissionId]) returns IO.raiseError(testException)

        permissionService
          .deletePermission(publicApplicationId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on getBy(:permissionId)" should {

    "call PermissionRepository" in {
      permissionRepository.getBy(any[ApplicationId], any[PermissionId]) returns IO.pure(Some(permission_1))

      for {
        _ <- permissionService.getBy(publicApplicationId_1, publicPermissionId_1)

        _ = verify(permissionRepository).getBy(eqTo(publicApplicationId_1), eqTo(publicPermissionId_1))
      } yield ()
    }

    "return the value returned by PermissionRepository" when {

      "PermissionRepository returns empty Option" in {
        permissionRepository.getBy(any[ApplicationId], any[PermissionId]) returns IO.pure(None)

        permissionService.getBy(publicApplicationId_1, publicPermissionId_1).asserting(_ shouldBe None)
      }

      "PermissionRepository returns non-empty Option" in {
        permissionRepository.getBy(any[ApplicationId], any[PermissionId]) returns IO.pure(Some(permission_1))

        permissionService.getBy(publicApplicationId_1, publicPermissionId_1).asserting(_ shouldBe Some(permission_1))
      }
    }

    "return failed IO" when {
      "PermissionRepository returns failed IO" in {
        permissionRepository.getBy(any[ApplicationId], any[PermissionId]) returns IO.raiseError(testException)

        permissionService
          .getBy(publicApplicationId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on getAllBy" when {

    val nameFragment = Some("test:name:fragment")

    "everything works correctly" should {

      "call ApplicationRepository and PermissionRepository" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.pure(Option(application_1))
        permissionRepository.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(List.empty)

        for {
          _ <- permissionService.getAllBy(publicApplicationId_1)(nameFragment)

          _ = verify(applicationRepository).getBy(eqTo(publicApplicationId_1))
          _ = verify(permissionRepository).getAllBy(eqTo(publicApplicationId_1))(eqTo(nameFragment))
        } yield ()
      }

      "return the value returned by PermissionRepository" when {

        "PermissionRepository returns empty List" in {
          applicationRepository.getBy(any[ApplicationId]) returns IO.pure(Option(application_1))
          permissionRepository.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(List.empty)

          permissionService
            .getAllBy(publicApplicationId_1)(nameFragment)
            .asserting(_ shouldBe Right(List.empty[Permission]))
        }

        "PermissionRepository returns non-empty List" in {
          applicationRepository.getBy(any[ApplicationId]) returns IO.pure(Option(application_1))
          permissionRepository.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.pure(
            List(permission_1, permission_2, permission_3)
          )

          permissionService
            .getAllBy(publicApplicationId_1)(nameFragment)
            .asserting(_ shouldBe Right(List(permission_1, permission_2, permission_3)))
        }
      }
    }

    "ApplicationRepository returns empty Option" should {

      "NOT call PermissionRepository" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.pure(none[Application])

        for {
          _ <- permissionService.getAllBy(publicApplicationId_1)(nameFragment)

          _ = verifyZeroInteractions(permissionRepository)
        } yield ()
      }

      "return Left containing ApplicationNotFoundError" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.pure(none[Application])

        permissionService
          .getAllBy(publicApplicationId_1)(nameFragment)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }
    }

    "PermissionRepository returns failed IO" should {
      "return failed IO" in {
        applicationRepository.getBy(any[ApplicationId]) returns IO.pure(Option(application_1))
        permissionRepository.getAllBy(any[ApplicationId])(any[Option[String]]) returns IO.raiseError(testException)

        permissionService
          .getAllBy(publicApplicationId_1)(nameFragment)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
