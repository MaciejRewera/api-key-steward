package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.RepositoryErrors.ResourceServerDbError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerNotFoundError
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ResourceServer, ResourceServerUpdate}
import apikeysteward.repositories.ResourceServerRepository
import apikeysteward.routes.model.admin.resourceserver.{CreateResourceServerRequest, UpdateResourceServerRequest}
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

class ResourceServerServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val resourceServerRepository = mock[ResourceServerRepository]

  private val resourceServerService = new ResourceServerService(uuidGenerator, resourceServerRepository)

  override def beforeEach(): Unit =
    reset(uuidGenerator, resourceServerRepository)

  private val testException = new RuntimeException("Test Exception")

  "ResourceServerService on createResourceServer" when {

    val createResourceServerRequest =
      CreateResourceServerRequest(
        name = resourceServerName_1,
        description = resourceServerDescription_1,
        permissions = List(createPermissionRequest_1, createPermissionRequest_2, createPermissionRequest_3)
      )

    val resourceServer = resourceServer_1.copy(permissions = List(permission_1, permission_2, permission_3))

    val resourceServerAlreadyExistsError = ResourceServerAlreadyExistsError(publicResourceServerIdStr_1)

    "everything works correctly" should {

      "call UuidGenerator and ResourceServerRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.pure(Right(resourceServer))

        for {
          _ <- resourceServerService.createResourceServer(publicTenantId_1, createResourceServerRequest)

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(resourceServerRepository).insert(eqTo(publicTenantId_1), eqTo(resourceServer))
        } yield ()
      }

      "return the newly created ResourceServer returned by ResourceServerRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.pure(Right(resourceServer))

        resourceServerService
          .createResourceServer(publicTenantId_1, createResourceServerRequest)
          .asserting(_ shouldBe Right(resourceServer))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call ResourceServerRepository" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- resourceServerService.createResourceServer(publicTenantId_1, createResourceServerRequest).attempt

          _ = verifyZeroInteractions(resourceServerRepository)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        resourceServerService
          .createResourceServer(publicTenantId_1, createResourceServerRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ResourceServerRepository returns Left containing ResourceServerAlreadyExistsError on the first try" should {

      "call UuidGenerator and ResourceServerRepository again" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        val insertedResourceServer = resourceServer.copy(resourceServerId = publicResourceServerId_2)
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns (
          IO.pure(Left(resourceServerAlreadyExistsError)),
          IO.pure(Right(insertedResourceServer))
        )

        for {
          _ <- resourceServerService.createResourceServer(publicTenantId_1, createResourceServerRequest)

          _ = verify(uuidGenerator, times(8)).generateUuid
          _ = verify(resourceServerRepository).insert(eqTo(publicTenantId_1), eqTo(resourceServer))
          _ = verify(resourceServerRepository).insert(eqTo(publicTenantId_1), eqTo(insertedResourceServer))
        } yield ()
      }

      "return the second created ResourceServer returned by ResourceServerRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        val insertedResourceServer = resourceServer.copy(resourceServerId = publicResourceServerId_2)
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns (
          IO.pure(Left(resourceServerAlreadyExistsError)),
          IO.pure(Right(insertedResourceServer))
        )

        resourceServerService
          .createResourceServer(publicTenantId_1, createResourceServerRequest)
          .asserting(_ shouldBe Right(insertedResourceServer))
      }
    }

    "ResourceServerRepository keeps returning Left containing ResourceServerAlreadyExistsError" should {

      "call UuidGenerator and ResourceServerRepository again until reaching max retries amount" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_3),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_4),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
        )
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.pure(
          Left(resourceServerAlreadyExistsError)
        )

        for {
          _ <- resourceServerService.createResourceServer(publicTenantId_1, createResourceServerRequest)

          _ = verify(uuidGenerator, times(16)).generateUuid
          _ = verify(resourceServerRepository).insert(eqTo(publicTenantId_1), eqTo(resourceServer))
          _ = verify(resourceServerRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(resourceServer.copy(resourceServerId = publicResourceServerId_2))
          )
          _ = verify(resourceServerRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(resourceServer.copy(resourceServerId = publicResourceServerId_3))
          )
          _ = verify(resourceServerRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(resourceServer.copy(resourceServerId = publicResourceServerId_4))
          )
        } yield ()
      }

      "return successful IO containing Left with ResourceServerAlreadyExistsError" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_2),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_3),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
          IO.pure(publicResourceServerId_4),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3),
        )
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.pure(
          Left(resourceServerAlreadyExistsError)
        )

        resourceServerService
          .createResourceServer(publicTenantId_1, createResourceServerRequest)
          .asserting(_ shouldBe Left(resourceServerAlreadyExistsError))
      }
    }

    val testSqlException = new SQLException("Test SQL Exception")

    Seq(
      ReferencedTenantDoesNotExistError(publicTenantId_1),
      ResourceServerInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"ResourceServerRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "NOT call UuidGenerator or ResourceServerRepository again" in {
          uuidGenerator.generateUuid returns (
            IO.pure(publicResourceServerId_1),
            IO.pure(publicPermissionId_1),
            IO.pure(publicPermissionId_2),
            IO.pure(publicPermissionId_3)
          )
          resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.pure(
            Left(insertionError)
          )

          for {
            _ <- resourceServerService.createResourceServer(publicTenantId_1, createResourceServerRequest)

            _ = verify(uuidGenerator, times(4)).generateUuid
            _ = verify(resourceServerRepository).insert(eqTo(publicTenantId_1), eqTo(resourceServer))
          } yield ()
        }

        "return failed IO containing this error" in {
          uuidGenerator.generateUuid returns (
            IO.pure(publicResourceServerId_1),
            IO.pure(publicPermissionId_1),
            IO.pure(publicPermissionId_2),
            IO.pure(publicPermissionId_3)
          )
          resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.pure(
            Left(insertionError)
          )

          resourceServerService
            .createResourceServer(publicTenantId_1, createResourceServerRequest)
            .asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ResourceServerRepository returns failed IO" should {

      "NOT call UuidGenerator or ResourceServerRepository again" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.raiseError(testException)

        for {
          _ <- resourceServerService.createResourceServer(publicTenantId_1, createResourceServerRequest).attempt

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(resourceServerRepository).insert(eqTo(publicTenantId_1), eqTo(resourceServer))
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicResourceServerId_1),
          IO.pure(publicPermissionId_1),
          IO.pure(publicPermissionId_2),
          IO.pure(publicPermissionId_3)
        )
        resourceServerRepository.insert(any[UUID], any[ResourceServer]) returns IO.raiseError(testException)

        resourceServerService
          .createResourceServer(publicTenantId_1, createResourceServerRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerService on updateResourceServer" should {

    val updateResourceServerRequest =
      UpdateResourceServerRequest(name = resourceServerNameUpdated, description = resourceServerDescriptionUpdated)

    val updatedResourceServer =
      resourceServer_1.copy(name = resourceServerNameUpdated, description = resourceServerDescriptionUpdated)

    "call ResourceServerRepository" in {
      resourceServerRepository.update(any[ResourceServerUpdate]) returns IO.pure(Right(updatedResourceServer))

      for {
        _ <- resourceServerService.updateResourceServer(publicResourceServerId_1, updateResourceServerRequest)

        _ = verify(resourceServerRepository).update(eqTo(resourceServerUpdate_1))
      } yield ()
    }

    "return value returned by ResourceServerRepository" when {

      "ResourceServerRepository returns Right" in {
        resourceServerRepository.update(any[ResourceServerUpdate]) returns IO.pure(Right(updatedResourceServer))

        resourceServerService
          .updateResourceServer(publicResourceServerId_1, updateResourceServerRequest)
          .asserting(_ shouldBe Right(updatedResourceServer))
      }

      "ResourceServerRepository returns Left" in {
        resourceServerRepository.update(any[ResourceServerUpdate]) returns IO.pure(
          Left(ResourceServerNotFoundError(publicResourceServerIdStr_1))
        )

        resourceServerService
          .updateResourceServer(publicResourceServerId_1, updateResourceServerRequest)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }
    }

    "return failed IO" when {
      "ResourceServerRepository returns failed IO" in {
        resourceServerRepository.update(any[ResourceServerUpdate]) returns IO.raiseError(testException)

        resourceServerService
          .updateResourceServer(publicResourceServerId_1, updateResourceServerRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerService on reactivateResourceServer" should {

    val reactivatedResourceServer = resourceServer_1.copy(isActive = true)

    "call ResourceServerRepository" in {
      resourceServerRepository.activate(any[UUID]) returns IO.pure(Right(reactivatedResourceServer))

      for {
        _ <- resourceServerService.reactivateResourceServer(publicResourceServerId_1)

        _ = verify(resourceServerRepository).activate(eqTo(publicResourceServerId_1))
      } yield ()
    }

    "return value returned by ResourceServerRepository" when {

      "ResourceServerRepository returns Right" in {
        resourceServerRepository.activate(any[UUID]) returns IO.pure(Right(reactivatedResourceServer))

        resourceServerService
          .reactivateResourceServer(publicResourceServerId_1)
          .asserting(_ shouldBe Right(reactivatedResourceServer))
      }

      "ResourceServerRepository returns Left" in {
        resourceServerRepository.activate(any[UUID]) returns IO.pure(
          Left(ResourceServerNotFoundError(publicResourceServerIdStr_1))
        )

        resourceServerService
          .reactivateResourceServer(publicResourceServerId_1)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }
    }

    "return failed IO" when {
      "ResourceServerRepository returns failed IO" in {
        resourceServerRepository.activate(any[UUID]) returns IO.raiseError(testException)

        resourceServerService
          .reactivateResourceServer(publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerService on deactivateResourceServer" should {

    val deactivatedResourceServer = resourceServer_1.copy(isActive = false)

    "call ResourceServerRepository" in {
      resourceServerRepository.deactivate(any[UUID]) returns IO.pure(Right(deactivatedResourceServer))

      for {
        _ <- resourceServerService.deactivateResourceServer(publicResourceServerId_1)

        _ = verify(resourceServerRepository).deactivate(eqTo(publicResourceServerId_1))
      } yield ()
    }

    "return value returned by ResourceServerRepository" when {

      "ResourceServerRepository returns Right" in {
        resourceServerRepository.deactivate(any[UUID]) returns IO.pure(Right(deactivatedResourceServer))

        resourceServerService
          .deactivateResourceServer(publicResourceServerId_1)
          .asserting(_ shouldBe Right(deactivatedResourceServer))
      }

      "ResourceServerRepository returns Left" in {
        resourceServerRepository.deactivate(any[UUID]) returns IO.pure(
          Left(ResourceServerNotFoundError(publicResourceServerIdStr_1))
        )

        resourceServerService
          .deactivateResourceServer(publicResourceServerId_1)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }
    }

    "return failed IO" when {
      "ResourceServerRepository returns failed IO" in {
        resourceServerRepository.deactivate(any[UUID]) returns IO.raiseError(testException)

        resourceServerService
          .deactivateResourceServer(publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerService on deleteResourceServer" should {

    val deletedResourceServer = resourceServer_1.copy(isActive = false)

    "call ResourceServerRepository" in {
      resourceServerRepository.delete(any[UUID]) returns IO.pure(Right(deletedResourceServer))

      for {
        _ <- resourceServerService.deleteResourceServer(publicResourceServerId_1)

        _ = verify(resourceServerRepository).delete(eqTo(publicResourceServerId_1))
      } yield ()
    }

    "return value returned by ResourceServerRepository" when {

      "ResourceServerRepository returns Right" in {
        resourceServerRepository.delete(any[UUID]) returns IO.pure(Right(deletedResourceServer))

        resourceServerService
          .deleteResourceServer(publicResourceServerId_1)
          .asserting(_ shouldBe Right(deletedResourceServer))
      }

      "ResourceServerRepository returns Left" in {
        resourceServerRepository.delete(any[UUID]) returns IO.pure(
          Left(ResourceServerDbError.resourceServerNotFoundError(publicResourceServerIdStr_1))
        )

        resourceServerService
          .deleteResourceServer(publicResourceServerId_1)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }
    }

    "return failed IO" when {
      "ResourceServerRepository returns failed IO" in {
        resourceServerRepository.delete(any[UUID]) returns IO.raiseError(testException)

        resourceServerService
          .deleteResourceServer(publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerService on getBy(:resourceServerId)" should {

    "call ResourceServerRepository" in {
      resourceServerRepository.getBy(any[ResourceServerId]) returns IO.pure(Some(resourceServer_1))

      for {
        _ <- resourceServerService.getBy(publicResourceServerId_1)

        _ = verify(resourceServerRepository).getBy(eqTo(publicResourceServerId_1))
      } yield ()
    }

    "return the value returned by ResourceServerRepository" when {

      "ResourceServerRepository returns empty Option" in {
        resourceServerRepository.getBy(any[ResourceServerId]) returns IO.pure(None)

        resourceServerService.getBy(publicResourceServerId_1).asserting(_ shouldBe None)
      }

      "ResourceServerRepository returns non-empty Option" in {
        resourceServerRepository.getBy(any[ResourceServerId]) returns IO.pure(Some(resourceServer_1))

        resourceServerService.getBy(publicResourceServerId_1).asserting(_ shouldBe Some(resourceServer_1))
      }
    }

    "return failed IO" when {
      "ResourceServerRepository returns failed IO" in {
        resourceServerRepository.getBy(any[ResourceServerId]) returns IO.raiseError(testException)

        resourceServerService.getBy(publicResourceServerId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerService on getAllForTenant" should {

    "call ResourceServerRepository" in {
      resourceServerRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

      for {
        _ <- resourceServerService.getAllForTenant(publicTenantId_1)

        _ = verify(resourceServerRepository).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "return the value returned by ResourceServerRepository" when {

      "ResourceServerRepository returns empty List" in {
        resourceServerRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        resourceServerService.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[ResourceServer])
      }

      "ResourceServerRepository returns non-empty List" in {
        resourceServerRepository.getAllForTenant(any[TenantId]) returns IO.pure(
          List(resourceServer_1, resourceServer_2, resourceServer_3)
        )

        resourceServerService
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(resourceServer_1, resourceServer_2, resourceServer_3))
      }
    }

    "return failed IO" when {
      "ResourceServerRepository returns failed IO" in {
        resourceServerRepository.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        resourceServerService.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
