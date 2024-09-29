package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantInsertionError, TenantNotFoundError}
import apikeysteward.model.{Tenant, TenantUpdate}
import apikeysteward.repositories.TenantRepository
import apikeysteward.routes.model.admin.tenant.{CreateTenantRequest, UpdateTenantRequest}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class TenantServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with FixedClock with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val tenantRepository = mock[TenantRepository]

  private val tenantService = new TenantService(uuidGenerator, tenantRepository)

  override def beforeEach(): Unit =
    reset(uuidGenerator, tenantRepository)

  private val testException = new RuntimeException("Test Exception")

  "TenantService on createTenant" when {

    val createTenantRequest = CreateTenantRequest(name = tenantName_1)

    val insertionError = TenantInsertionError.tenantAlreadyExistsError(publicKeyIdStr_1)

    "everything works correctly" should {

      "call TenantRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicTenantId_1)
        tenantRepository.insert(any[Tenant]) returns IO.pure(Right(tenant_1))

        for {
          _ <- tenantService.createTenant(createTenantRequest)

          _ = verify(tenantRepository).insert(eqTo(tenant_1))
        } yield ()
      }

      "return the newly created Tenant returned by TenantRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicTenantId_1)
        tenantRepository.insert(any[Tenant]) returns IO.pure(Right(tenant_1))

        tenantService.createTenant(createTenantRequest).asserting(_ shouldBe Right(tenant_1))
      }
    }

    "TenantRepository returns Left containing TenantAlreadyExistsError on the first try" should {

      "call UuidGenerator and TenantRepository again" in {
        uuidGenerator.generateUuid returns (IO.pure(publicTenantId_1), IO.pure(publicTenantId_2))
        val insertedTenant = tenant_1.copy(tenantId = publicTenantId_2)
        tenantRepository.insert(any[Tenant]) returns (
          IO.pure(Left(insertionError)),
          IO.pure(Right(insertedTenant))
        )

        for {
          _ <- tenantService.createTenant(createTenantRequest)

          _ = verify(uuidGenerator, times(2)).generateUuid
          _ = verify(tenantRepository).insert(eqTo(tenant_1))
          _ = verify(tenantRepository).insert(eqTo(insertedTenant))
        } yield ()
      }

      "return the second created Tenant returned by TenantRepository" in {
        uuidGenerator.generateUuid returns (IO.pure(publicTenantId_1), IO.pure(publicTenantId_2))
        val insertedTenant = tenant_1.copy(tenantId = publicTenantId_2)
        tenantRepository.insert(any[Tenant]) returns (
          IO.pure(Left(insertionError)),
          IO.pure(Right(insertedTenant))
        )

        tenantService.createTenant(createTenantRequest).asserting(_ shouldBe Right(insertedTenant))
      }
    }

    "TenantRepository keeps returning Left containing TenantAlreadyExistsError on the first try" should {

      "call UuidGenerator and TenantRepository again until reaching max retries amount" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicTenantId_1), IO.pure(publicTenantId_2), IO.pure(publicTenantId_3), IO.pure(publicTenantId_4)
        )
        tenantRepository.insert(any[Tenant]) returns IO.pure(Left(insertionError))

        for {
          _ <- tenantService.createTenant(createTenantRequest)

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(tenantRepository).insert(eqTo(tenant_1))
          _ = verify(tenantRepository).insert(eqTo(tenant_1.copy(tenantId = publicTenantId_2)))
          _ = verify(tenantRepository).insert(eqTo(tenant_1.copy(tenantId = publicTenantId_3)))
          _ = verify(tenantRepository).insert(eqTo(tenant_1.copy(tenantId = publicTenantId_4)))
        } yield ()
      }

      "return successful IO containing Left with TenantInsertionError" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicTenantId_1), IO.pure(publicTenantId_2), IO.pure(publicTenantId_3), IO.pure(publicTenantId_4)
        )
        tenantRepository.insert(any[Tenant]) returns IO.pure(Left(insertionError))

        tenantService.createTenant(createTenantRequest).asserting(_ shouldBe Left(insertionError))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantRepository" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- tenantService.createTenant(createTenantRequest).attempt

          _ = verifyZeroInteractions(tenantRepository)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        tenantService.createTenant(createTenantRequest).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "TenantRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(publicTenantId_1)
        tenantRepository.insert(any[Tenant]) returns IO.raiseError(testException)

        tenantService.createTenant(createTenantRequest).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantService on updateTenant" should {

    val updateTenantRequest = UpdateTenantRequest(name = tenantNameUpdated)

    "call TenantRepository" in {
      tenantRepository.update(any[TenantUpdate]) returns IO.pure(Right(tenant_1))

      for {
        _ <- tenantService.updateTenant(publicTenantId_1, updateTenantRequest)

        expectedTenantUpdate = TenantUpdate(tenantId = publicTenantId_1, name = tenantNameUpdated)
        _ = verify(tenantRepository).update(eqTo(expectedTenantUpdate))
      } yield ()
    }

    "return value returned by TenantRepository" when {

      "TenantRepository returns Right" in {
        tenantRepository.update(any[TenantUpdate]) returns IO.pure(Right(tenant_1))

        tenantService.updateTenant(publicTenantId_1, updateTenantRequest).asserting(_ shouldBe Right(tenant_1))
      }

      "TenantRepository returns Left" in {
        tenantRepository.update(any[TenantUpdate]) returns IO.pure(Left(TenantNotFoundError(publicTenantIdStr_1)))

        tenantService
          .updateTenant(publicTenantId_1, updateTenantRequest)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }
    }

    "return failed IO" when {
      "TenantRepository returns failed IO" in {
        tenantRepository.update(any[TenantUpdate]) returns IO.raiseError(testException)

        tenantService
          .updateTenant(publicTenantId_1, updateTenantRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantService on reactivateTenant" should {

    "call TenantRepository" in {
      tenantRepository.activate(any[UUID]) returns IO.pure(Right(tenant_1))

      for {
        _ <- tenantService.reactivateTenant(publicTenantId_1)

        _ = verify(tenantRepository).activate(eqTo(publicTenantId_1))
      } yield ()
    }

    "return value returned by TenantRepository" when {

      "TenantRepository returns Right" in {
        tenantRepository.activate(any[UUID]) returns IO.pure(Right(tenant_1))

        tenantService.reactivateTenant(publicTenantId_1).asserting(_ shouldBe Right(tenant_1))
      }

      "TenantRepository returns Left" in {
        tenantRepository.activate(any[UUID]) returns IO.pure(Left(TenantNotFoundError(publicKeyIdStr_1)))

        tenantService
          .reactivateTenant(publicTenantId_1)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicKeyIdStr_1)))
      }
    }

    "return failed IO" when {
      "TenantRepository returns failed IO" in {
        tenantRepository.activate(any[UUID]) returns IO.raiseError(testException)

        tenantService.reactivateTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantService on deactivateTenant" should {

    "call TenantRepository" in {
      tenantRepository.deactivate(any[UUID]) returns IO.pure(Right(tenant_1))

      for {
        _ <- tenantService.deactivateTenant(publicTenantId_1)

        _ = verify(tenantRepository).deactivate(eqTo(publicTenantId_1))
      } yield ()
    }

    "return value returned by TenantRepository" when {

      "TenantRepository returns Right" in {
        tenantRepository.deactivate(any[UUID]) returns IO.pure(Right(tenant_1))

        tenantService.deactivateTenant(publicTenantId_1).asserting(_ shouldBe Right(tenant_1))
      }

      "TenantRepository returns Left" in {
        tenantRepository.deactivate(any[UUID]) returns IO.pure(Left(TenantNotFoundError(publicKeyIdStr_1)))

        tenantService
          .deactivateTenant(publicTenantId_1)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicKeyIdStr_1)))
      }
    }

    "return failed IO" when {
      "TenantRepository returns failed IO" in {
        tenantRepository.deactivate(any[UUID]) returns IO.raiseError(testException)

        tenantService.deactivateTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantService on deleteTenant" should {

    "call TenantRepository" in {
      tenantRepository.delete(any[UUID]) returns IO.pure(Right(tenant_1))

      for {
        _ <- tenantService.deleteTenant(publicTenantId_1)

        _ = verify(tenantRepository).delete(eqTo(publicTenantId_1))
      } yield ()
    }

    "return value returned by TenantRepository" when {

      "TenantRepository returns Right" in {
        tenantRepository.delete(any[UUID]) returns IO.pure(Right(tenant_1))

        tenantService.deleteTenant(publicTenantId_1).asserting(_ shouldBe Right(tenant_1))
      }

      "TenantRepository returns Left" in {
        tenantRepository.delete(any[UUID]) returns IO.pure(Left(TenantNotFoundError(publicKeyIdStr_1)))

        tenantService
          .deleteTenant(publicTenantId_1)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicKeyIdStr_1)))
      }
    }

    "return failed IO" when {
      "TenantRepository returns failed IO" in {
        tenantRepository.delete(any[UUID]) returns IO.raiseError(testException)

        tenantService.deleteTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantService on getBy(:tenantId)" should {

    "call TenantRepository" in {
      tenantRepository.getBy(any[UUID]) returns IO.pure(Some(tenant_1))

      for {
        _ <- tenantService.getBy(publicTenantId_1)

        _ = verify(tenantRepository).getBy(eqTo(publicTenantId_1))
      } yield ()
    }

    "return the value returned by TenantRepository" when {

      "TenantRepository returns empty Option" in {
        tenantRepository.getBy(any[UUID]) returns IO.pure(None)

        tenantService.getBy(publicTenantId_1).asserting(_ shouldBe None)
      }
      "TenantRepository returns non-empty Option" in {
        tenantRepository.getBy(any[UUID]) returns IO.pure(Some(tenant_1))

        tenantService.getBy(publicTenantId_1).asserting(_ shouldBe Some(tenant_1))
      }
    }
  }

  "TenantService on getAll" should {

    "call TenantRepository" in {
      tenantRepository.getAll returns IO.pure(List.empty)

      for {
        _ <- tenantService.getAll

        _ = verify(tenantRepository).getAll
      } yield ()
    }

    "return the value returned by TenantRepository" when {

      "TenantRepository returns empty List" in {
        tenantRepository.getAll returns IO.pure(List.empty)

        tenantService.getAll.asserting(_ shouldBe List.empty[Tenant])
      }
      "TenantRepository returns non-empty List" in {
        tenantRepository.getAll returns IO.pure(List(tenant_1, tenant_2, tenant_3))

        tenantService.getAll.asserting(_ shouldBe List(tenant_1, tenant_2, tenant_3))
      }
    }
  }

}
