package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{apiKeyTemplate_1, publicTemplateId_1}
import apikeysteward.base.testdata.ResourceServersTestData.{
  publicResourceServerIdStr_1,
  publicResourceServerId_1,
  resourceServerDbId_1,
  resourceServer_1
}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.GenericError
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerNotFoundError
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionNotFoundError
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ApiKeyTemplate, Permission, ResourceServer}
import apikeysteward.repositories.{ApiKeyTemplateRepository, PermissionRepository, ResourceServerRepository}
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

class PermissionServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val permissionRepository = mock[PermissionRepository]
  private val resourceServerRepository = mock[ResourceServerRepository]
  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]

  private val permissionService =
    new PermissionService(uuidGenerator, permissionRepository, resourceServerRepository, apiKeyTemplateRepository)

  override def beforeEach(): Unit =
    reset(uuidGenerator, permissionRepository, resourceServerRepository, apiKeyTemplateRepository)

  private val testException = new RuntimeException("Test Exception")

  "PermissionService on createPermission" when {

    val createPermissionRequest =
      CreatePermissionRequest(name = permissionName_1, description = permissionDescription_1)

    val permissionAlreadyExistsError = PermissionAlreadyExistsError(publicPermissionIdStr_1)

    "everything works correctly" should {

      "call UuidGenerator and PermissionRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.pure(Right(permission_1))

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

          _ = verify(uuidGenerator).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
        } yield ()
      }

      "return the newly created Permission returned by PermissionRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.pure(Right(permission_1))

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
        val insertedPermission = permission_1.copy(publicPermissionId = publicPermissionId_2)
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns (
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
        val insertedPermission = permission_1.copy(publicPermissionId = publicPermissionId_2)
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns (
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
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.pure(
          Left(permissionAlreadyExistsError)
        )

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
          _ = verify(permissionRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(permission_1.copy(publicPermissionId = publicPermissionId_2))
          )
          _ = verify(permissionRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(permission_1.copy(publicPermissionId = publicPermissionId_3))
          )
          _ = verify(permissionRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(permission_1.copy(publicPermissionId = publicPermissionId_4))
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
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.pure(
          Left(permissionAlreadyExistsError)
        )

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .asserting(_ shouldBe Left(permissionAlreadyExistsError))
      }
    }

    val resourceServerId = resourceServerDbId_1
    val testSqlException = new SQLException("Test SQL Exception")

    Seq(
      PermissionAlreadyExistsForThisResourceServerError(permissionName_1, resourceServerId),
      ReferencedResourceServerDoesNotExistError(publicTenantId_1),
      PermissionInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"PermissionRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "NOT call UuidGenerator or PermissionRepository again" in {
          uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
          permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.pure(
            Left(insertionError)
          )

          for {
            _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest)

            _ = verify(uuidGenerator).generateUuid
            _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
          } yield ()
        }

        "return failed IO containing this error" in {
          uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
          permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.pure(
            Left(insertionError)
          )

          permissionService
            .createPermission(publicTenantId_1, createPermissionRequest)
            .asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "PermissionRepository returns failed IO" should {

      "NOT call UuidGenerator or PermissionRepository again" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.raiseError(testException)

        for {
          _ <- permissionService.createPermission(publicTenantId_1, createPermissionRequest).attempt

          _ = verify(uuidGenerator).generateUuid
          _ = verify(permissionRepository).insert(eqTo(publicTenantId_1), eqTo(permission_1))
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(publicPermissionId_1)
        permissionRepository.insert(any[ResourceServerId], any[Permission]) returns IO.raiseError(testException)

        permissionService
          .createPermission(publicTenantId_1, createPermissionRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on deletePermission" should {

    "call PermissionRepository" in {
      permissionRepository.delete(any[ResourceServerId], any[PermissionId]) returns IO.pure(Right(permission_1))

      for {
        _ <- permissionService.deletePermission(publicResourceServerId_1, publicPermissionId_1)

        _ = verify(permissionRepository).delete(eqTo(publicResourceServerId_1), eqTo(publicPermissionId_1))
      } yield ()
    }

    "return value returned by PermissionRepository" when {

      "PermissionRepository returns Right" in {
        permissionRepository.delete(any[ResourceServerId], any[PermissionId]) returns IO.pure(Right(permission_1))

        permissionService
          .deletePermission(publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Right(permission_1))
      }

      "PermissionRepository returns Left" in {
        permissionRepository.delete(any[ResourceServerId], any[PermissionId]) returns IO.pure(
          Left(PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1))
        )

        permissionService
          .deletePermission(publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Left(PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)))
      }
    }

    "return failed IO" when {
      "PermissionRepository returns failed IO" in {
        permissionRepository.delete(any[ResourceServerId], any[PermissionId]) returns IO.raiseError(testException)

        permissionService
          .deletePermission(publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on getBy(:resourceServerId, :permissionId)" should {

    "call PermissionRepository" in {
      permissionRepository.getBy(any[ResourceServerId], any[PermissionId]) returns IO.pure(Some(permission_1))

      for {
        _ <- permissionService.getBy(publicResourceServerId_1, publicPermissionId_1)

        _ = verify(permissionRepository).getBy(eqTo(publicResourceServerId_1), eqTo(publicPermissionId_1))
      } yield ()
    }

    "return the value returned by PermissionRepository" when {

      "PermissionRepository returns empty Option" in {
        permissionRepository.getBy(any[ResourceServerId], any[PermissionId]) returns IO.pure(None)

        permissionService.getBy(publicResourceServerId_1, publicPermissionId_1).asserting(_ shouldBe None)
      }

      "PermissionRepository returns non-empty Option" in {
        permissionRepository.getBy(any[ResourceServerId], any[PermissionId]) returns IO.pure(Some(permission_1))

        permissionService.getBy(publicResourceServerId_1, publicPermissionId_1).asserting(_ shouldBe Some(permission_1))
      }
    }

    "return failed IO" when {
      "PermissionRepository returns failed IO" in {
        permissionRepository.getBy(any[ResourceServerId], any[PermissionId]) returns IO.raiseError(testException)

        permissionService
          .getBy(publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on getAllForTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplateRepository and PermissionRepository" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
        permissionRepository.getAllFor(any[ApiKeyTemplateId]) returns IO.pure(
          List(permission_1, permission_2, permission_3)
        )

        for {
          _ <- permissionService.getAllForTemplate(publicTemplateId_1)

          _ = verify(apiKeyTemplateRepository).getBy(eqTo(publicTemplateId_1))
          _ = verify(permissionRepository).getAllFor(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return the value returned by PermissionRepository" when {

        "PermissionRepository returns empty List" in {
          apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
          permissionRepository.getAllFor(any[ApiKeyTemplateId]) returns IO.pure(List.empty[Permission])

          permissionService
            .getAllForTemplate(publicTemplateId_1)
            .asserting(_ shouldBe Right(List.empty[Permission]))
        }

        "PermissionRepository returns non-empty List" in {
          apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
          permissionRepository.getAllFor(any[ApiKeyTemplateId]) returns IO.pure(
            List(permission_1, permission_2, permission_3)
          )

          permissionService
            .getAllForTemplate(publicTemplateId_1)
            .asserting(_ shouldBe Right(List(permission_1, permission_2, permission_3)))
        }
      }
    }

    "ApiKeyTemplateRepository returns empty Option" should {

      "NOT call PermissionRepository" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(none[ApiKeyTemplate])

        for {
          _ <- permissionService.getAllForTemplate(publicTemplateId_1)

          _ = verifyZeroInteractions(permissionRepository)
        } yield ()
      }

      "return Left containing ApiKeyTemplateDoesNotExist" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(none[ApiKeyTemplate])

        permissionService
          .getAllForTemplate(publicTemplateId_1)
          .asserting(_ shouldBe Left(GenericError.ApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "PermissionRepository returns failed IO containing exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Option(apiKeyTemplate_1))
        permissionRepository.getAllFor(any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        permissionService
          .getAllForTemplate(publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionService on getAllBy" when {

    val nameFragment = Some("test:name:fragment")

    "everything works correctly" should {

      "call ResourceServerRepository and PermissionRepository" in {
        resourceServerRepository.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(Option(resourceServer_1))
        permissionRepository.getAllBy(any[ResourceServerId])(any[Option[String]]) returns IO.pure(List.empty)

        for {
          _ <- permissionService.getAllBy(publicResourceServerId_1)(nameFragment)

          _ = verify(resourceServerRepository).getBy(
//            eqTo(publicTenantId_1),
            any[TenantId],
            eqTo(publicResourceServerId_1)
          )
          _ = verify(permissionRepository).getAllBy(eqTo(publicResourceServerId_1))(eqTo(nameFragment))
        } yield ()
      }

      "return the value returned by PermissionRepository" when {

        "PermissionRepository returns empty List" in {
          resourceServerRepository.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(Option(resourceServer_1))
          permissionRepository.getAllBy(any[ResourceServerId])(any[Option[String]]) returns IO.pure(List.empty)

          permissionService
            .getAllBy(publicResourceServerId_1)(nameFragment)
            .asserting(_ shouldBe Right(List.empty[Permission]))
        }

        "PermissionRepository returns non-empty List" in {
          resourceServerRepository.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(Option(resourceServer_1))
          permissionRepository.getAllBy(any[ResourceServerId])(any[Option[String]]) returns IO.pure(
            List(permission_1, permission_2, permission_3)
          )

          permissionService
            .getAllBy(publicResourceServerId_1)(nameFragment)
            .asserting(_ shouldBe Right(List(permission_1, permission_2, permission_3)))
        }
      }
    }

    "ResourceServerRepository returns empty Option" should {

      "NOT call PermissionRepository" in {
        resourceServerRepository.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(none[ResourceServer])

        for {
          _ <- permissionService.getAllBy(publicResourceServerId_1)(nameFragment)

          _ = verifyZeroInteractions(permissionRepository)
        } yield ()
      }

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerRepository.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(none[ResourceServer])

        permissionService
          .getAllBy(publicResourceServerId_1)(nameFragment)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }
    }

    "PermissionRepository returns failed IO" should {
      "return failed IO" in {
        resourceServerRepository.getBy(any[TenantId], any[ResourceServerId]) returns IO.pure(Option(resourceServer_1))
        permissionRepository.getAllBy(any[ResourceServerId])(any[Option[String]]) returns IO.raiseError(testException)

        permissionService
          .getAllBy(publicResourceServerId_1)(nameFragment)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
