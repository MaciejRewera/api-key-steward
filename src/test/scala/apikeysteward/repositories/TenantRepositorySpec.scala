package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.TenantDbError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantInsertionError, TenantNotFoundError}
import apikeysteward.model.Tenant
import apikeysteward.repositories.db.TenantDb
import apikeysteward.repositories.db.entity.TenantEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxApplicativeErrorId, catsSyntaxApplicativeId, catsSyntaxEitherId}
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.util.UUID

class TenantRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val tenantDb = mock[TenantDb]

  private val tenantRepository = new TenantRepository(tenantDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(tenantDb)

  private val tenantEntityRead_1: TenantEntity.Read = TenantEntity.Read(
    id = 1L,
    publicTenantId = publicTenantIdStr_1,
    name = tenantName_1,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )
  private val tenantEntityRead_2: TenantEntity.Read = tenantEntityRead_1.copy(
    id = 2L,
    publicTenantId = publicTenantIdStr_2,
    name = tenantName_2
  )
  private val tenantEntityRead_3: TenantEntity.Read = tenantEntityRead_1.copy(
    id = 3L,
    publicTenantId = publicTenantIdStr_3,
    name = tenantName_3
  )

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedOpt: doobie.ConnectionIO[Option[TenantEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, TenantEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, TenantEntity.Read]]

  "TenantRepository on insert" when {

    val tenantEntityReadWrapped = tenantEntityRead_1.asRight[TenantInsertionError].pure[doobie.ConnectionIO]

    val tenantInsertionError = TenantInsertionError.tenantAlreadyExistsError(publicTenantIdStr_1)
    val tenantInsertionErrorWrapped = tenantInsertionError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb" in {
        tenantDb.insert(any[TenantEntity.Write]) returns tenantEntityReadWrapped

        for {
          _ <- tenantRepository.insert(tenant_1)

          expectedTenantEntityWrite = TenantEntity.Write(publicTenantId = publicTenantIdStr_1, name = tenantName_1)
          _ = verify(tenantDb).insert(eqTo(expectedTenantEntityWrite))
        } yield ()
      }

      "return Right containing Tenant" in {
        tenantDb.insert(any[TenantEntity.Write]) returns tenantEntityReadWrapped

        tenantRepository.insert(tenant_1).asserting(_ shouldBe Right(tenant_1))
      }
    }

    "TenantDb returns Left containing TenantInsertionError" should {
      "return Left containing this error" in {
        tenantDb.insert(any[TenantEntity.Write]) returns tenantInsertionErrorWrapped

        tenantRepository.insert(tenant_1).asserting(_ shouldBe Left(tenantInsertionError))
      }
    }

    "TenantDb returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.insert(any[TenantEntity.Write]) returns testExceptionWrappedE[TenantInsertionError]

        tenantRepository.insert(tenant_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on update" when {

    val updatedTenantEntityReadWrapped =
      tenantEntityRead_1.copy(name = tenantNameUpdated).asRight[TenantNotFoundError].pure[doobie.ConnectionIO]

    val tenantNotFoundError = TenantNotFoundError(publicTenantIdStr_1)
    val tenantNotFoundErrorWrapped = tenantNotFoundError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb" in {
        tenantDb.update(any[TenantEntity.Update]) returns updatedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.update(tenantUpdate_1)

          expectedTenantEntityUpdate = TenantEntity.Update(
            publicTenantId = publicTenantIdStr_1,
            name = tenantNameUpdated
          )
          _ = verify(tenantDb).update(eqTo(expectedTenantEntityUpdate))
        } yield ()
      }

      "return Right containing updated Tenant" in {
        tenantDb.update(any[TenantEntity.Update]) returns updatedTenantEntityReadWrapped
        val expectedUpdatedTenant = tenant_1.copy(name = tenantNameUpdated)

        tenantRepository.update(tenantUpdate_1).asserting(_ shouldBe Right(expectedUpdatedTenant))
      }
    }

    "TenantDb returns Left containing TenantNotFoundError" should {
      "return Left containing this error" in {
        tenantDb.update(any[TenantEntity.Update]) returns tenantNotFoundErrorWrapped

        tenantRepository.update(tenantUpdate_1).asserting(_ shouldBe Left(tenantNotFoundError))
      }
    }

    "TenantDb returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.update(any[TenantEntity.Update]) returns testExceptionWrappedE[TenantNotFoundError]

        tenantRepository.update(tenantUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on getBy(:tenantId)" when {

    "should always call TenantDb" in {
      tenantDb.getByPublicTenantId(any[UUID]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]

      for {
        _ <- tenantRepository.getBy(publicTenantId_1)

        _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
      } yield ()
    }

    "TenantDb returns empty Option" should {
      "return empty Option" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns Option.empty[TenantEntity.Read].pure[doobie.ConnectionIO]

        tenantRepository.getBy(publicTenantId_1).asserting(_ shouldBe None)
      }
    }

    "TenantDb returns Option containing TenantEntity" should {
      "return Option containing Tenant" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]

        tenantRepository.getBy(publicTenantId_1).asserting(_ shouldBe Some(tenant_1))
      }
    }

    "TenantDb returns exception" should {
      "return failed IO containing this exception" in {
        tenantDb.getByPublicTenantId(any[UUID]) returns testExceptionWrappedOpt

        tenantRepository.getBy(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on getAll" when {

    "should always call TenantDb" in {
      tenantDb.getAll returns Stream.empty

      for {
        _ <- tenantRepository.getAll

        _ = verify(tenantDb).getAll
      } yield ()
    }

    "TenantDb returns empty Stream" should {
      "return empty List" in {
        tenantDb.getAll returns Stream.empty

        tenantRepository.getAll.asserting(_ shouldBe List.empty[Tenant])
      }
    }

    "TenantDb returns TenantEntities in Stream" should {
      "return List containing Tenants" in {
        tenantDb.getAll returns Stream(tenantEntityRead_1, tenantEntityRead_2, tenantEntityRead_3)

        tenantRepository.getAll.asserting(_ shouldBe List(tenant_1, tenant_2, tenant_3))
      }
    }

    "TenantDb returns exception" should {
      "return failed IO containing this exception" in {
        tenantDb.getAll returns Stream.raiseError[doobie.ConnectionIO](testException)

        tenantRepository.getAll.attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on activate" when {

    val activatedTenantEntityRead = tenantEntityRead_1.copy(deactivatedAt = None)
    val activatedTenantEntityReadWrapped =
      activatedTenantEntityRead.asRight[TenantNotFoundError].pure[doobie.ConnectionIO]

    val tenantNotFoundError = TenantNotFoundError(publicTenantIdStr_1)
    val tenantNotFoundErrorWrapped = tenantNotFoundError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb" in {
        tenantDb.activate(any[UUID]) returns activatedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.activate(publicTenantId_1)

          _ = verify(tenantDb).activate(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing activated Tenant" in {
        tenantDb.activate(any[UUID]) returns activatedTenantEntityReadWrapped
        val expectedActivatedTenant = tenant_1.copy(isActive = true)

        tenantRepository.activate(publicTenantId_1).asserting(_ shouldBe Right(expectedActivatedTenant))
      }
    }

    "TenantDb returns Left containing TenantNotFoundError" should {
      "return Left containing this error" in {
        tenantDb.activate(any[UUID]) returns tenantNotFoundErrorWrapped

        tenantRepository.activate(publicTenantId_1).asserting(_ shouldBe Left(tenantNotFoundError))
      }
    }

    "TenantDb returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.activate(any[UUID]) returns testExceptionWrappedE[TenantNotFoundError]

        tenantRepository.activate(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on deactivate" when {

    val deactivatedTenantEntityRead = tenantEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deactivatedTenantEntityReadWrapped =
      deactivatedTenantEntityRead.asRight[TenantNotFoundError].pure[doobie.ConnectionIO]

    val tenantNotFoundError = TenantNotFoundError(publicTenantIdStr_1)
    val tenantNotFoundErrorWrapped = tenantNotFoundError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb" in {
        tenantDb.deactivate(any[UUID]) returns deactivatedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.deactivate(publicTenantId_1)

          _ = verify(tenantDb).deactivate(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing deactivated Tenant" in {
        tenantDb.deactivate(any[UUID]) returns deactivatedTenantEntityReadWrapped
        val expectedDeactivatedTenant = tenant_1.copy(isActive = false)

        tenantRepository.deactivate(publicTenantId_1).asserting(_ shouldBe Right(expectedDeactivatedTenant))
      }
    }

    "TenantDb returns Left containing TenantNotFoundError" should {
      "return Left containing this error" in {
        tenantDb.deactivate(any[UUID]) returns tenantNotFoundErrorWrapped

        tenantRepository.deactivate(publicTenantId_1).asserting(_ shouldBe Left(tenantNotFoundError))
      }
    }

    "TenantDb returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.deactivate(any[UUID]) returns testExceptionWrappedE[TenantNotFoundError]

        tenantRepository.deactivate(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on delete" when {

    val deletedTenantEntityRead = tenantEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deletedTenantEntityReadWrapped = deletedTenantEntityRead.asRight[TenantDbError].pure[doobie.ConnectionIO]

    val tenantNotFoundError = TenantDbError.tenantNotFoundError(publicTenantIdStr_1)
    val tenantNotFoundErrorWrapped = tenantNotFoundError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    val tenantIsActiveError = TenantDbError.tenantIsNotDeactivatedError(publicTenantId_1)
    val tenantIsActiveErrorWrapped = tenantIsActiveError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb" in {
        tenantDb.deleteDeactivated(any[UUID]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(tenantDb).deleteDeactivated(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing deleted Tenant" in {
        tenantDb.deleteDeactivated(any[UUID]) returns deletedTenantEntityReadWrapped
        val expectedDeletedTenant = tenant_1.copy(isActive = false)

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Right(expectedDeletedTenant))
      }
    }

    "TenantDb returns Left containing TenantNotFoundError" should {
      "return Left containing this error" in {
        tenantDb.deleteDeactivated(any[UUID]) returns tenantNotFoundErrorWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Left(tenantNotFoundError))
      }
    }

    "TenantDb returns Left containing TenantNotdeactivatedError" should {
      "return Left containing this error" in {
        tenantDb.deleteDeactivated(any[UUID]) returns tenantIsActiveErrorWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Left(tenantIsActiveError))
      }
    }

    "TenantDb returns different exception" should {
      "return failed IO containing this exception" in {
        tenantDb.deleteDeactivated(any[UUID]) returns testExceptionWrappedE[TenantDbError]

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }
}
