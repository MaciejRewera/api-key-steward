package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ResourceServersTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData.{publicUserId_3, user_1, user_2, user_3}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.model.errors.ResourceServerDbError._
import apikeysteward.model.errors.TenantDbError.TenantInsertionError.TenantInsertionErrorImpl
import apikeysteward.model.errors.TenantDbError._
import apikeysteward.model.errors.UserDbError.UserNotFoundError
import apikeysteward.model.errors.{ResourceServerDbError, TenantDbError}
import apikeysteward.model.{ApiKeyTemplate, ResourceServer, Tenant, User}
import apikeysteward.repositories.UserRepository.UserRepositoryError
import apikeysteward.repositories.db.TenantDb
import apikeysteward.repositories.db.entity.TenantEntity
import apikeysteward.services.UuidGenerator
import cats.data.EitherT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.sql.SQLException
import java.util.UUID

class TenantRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val uuidGenerator = mock[UuidGenerator]
  private val tenantDb = mock[TenantDb]
  private val resourceServerRepository = mock[ResourceServerRepository]
  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]
  private val userRepository = mock[UserRepository]

  private val tenantRepository =
    new TenantRepository(uuidGenerator, tenantDb, resourceServerRepository, apiKeyTemplateRepository, userRepository)(
      noopTransactor
    )

  override def beforeEach(): Unit =
    reset(uuidGenerator, tenantDb, resourceServerRepository, apiKeyTemplateRepository, userRepository)

  private val tenantNotFoundError = TenantNotFoundError(publicTenantIdStr_1)
  private val tenantNotFoundErrorWrapped = tenantNotFoundError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")
  private val testSqlException = new SQLException("Test SQL Exception")

  private val testExceptionWrappedOpt: doobie.ConnectionIO[Option[TenantEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, TenantEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, TenantEntity.Read]]

  "TenantRepository on insert" when {

    val tenantEntityReadWrapped = tenantEntityRead_1.asRight[TenantInsertionError].pure[doobie.ConnectionIO]

    val tenantInsertionError: TenantInsertionError = TenantInsertionErrorImpl(testSqlException)
    val tenantInsertionErrorWrapped = tenantInsertionError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UuidGenerator and TenantDb" in {
        uuidGenerator.generateUuid returns IO.pure(tenantDbId_1)
        tenantDb.insert(any[TenantEntity.Write]) returns tenantEntityReadWrapped

        for {
          _ <- tenantRepository.insert(tenant_1)

          _ = verify(uuidGenerator).generateUuid
          _ = verify(tenantDb).insert(eqTo(tenantEntityWrite_1))
        } yield ()
      }

      "return Right containing Tenant" in {
        uuidGenerator.generateUuid returns IO.pure(tenantDbId_1)
        tenantDb.insert(any[TenantEntity.Write]) returns tenantEntityReadWrapped

        tenantRepository.insert(tenant_1).asserting(_ shouldBe Right(tenant_1))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantDb" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- tenantRepository.insert(tenant_1).attempt

          _ = verifyZeroInteractions(tenantDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        tenantRepository.insert(tenant_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "TenantDb returns Left containing TenantInsertionError" should {
      "return Left containing this error" in {
        uuidGenerator.generateUuid returns IO.pure(tenantDbId_1)
        tenantDb.insert(any[TenantEntity.Write]) returns tenantInsertionErrorWrapped

        tenantRepository.insert(tenant_1).asserting(_ shouldBe Left(tenantInsertionError))
      }
    }

    "TenantDb returns different exception" should {
      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(tenantDbId_1)
        tenantDb.insert(any[TenantEntity.Write]) returns testExceptionWrappedE[TenantInsertionError]

        tenantRepository.insert(tenant_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "TenantRepository on update" when {

    val updatedTenantEntityReadWrapped =
      tenantEntityRead_1.copy(name = tenantNameUpdated).asRight[TenantNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb" in {
        tenantDb.update(any[TenantEntity.Update]) returns updatedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.update(tenantUpdate_1)

          expectedTenantEntityUpdate = TenantEntity.Update(
            publicTenantId = publicTenantIdStr_1,
            name = tenantNameUpdated,
            description = tenantDescriptionUpdated
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

  "TenantRepository on activate" when {

    val activatedTenantEntityRead = tenantEntityRead_1.copy(deactivatedAt = None)
    val activatedTenantEntityReadWrapped =
      activatedTenantEntityRead.asRight[TenantNotFoundError].pure[doobie.ConnectionIO]

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

    val tenantNotFound = tenantNotFoundError.asInstanceOf[TenantDbError]
    val tenantNotFoundWrapped = tenantNotFound.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    val tenantIsNotDeactivatedError: TenantDbError = TenantIsNotDeactivatedError(publicTenantId_1)
    val tenantIsNotDeactivatedErrorWrapped =
      tenantIsNotDeactivatedError.asLeft[TenantEntity.Read].pure[doobie.ConnectionIO]

    val expectedDeletedTenant = tenant_1.copy(isActive = false)

    def initVerification(): Unit =
      tenantDb.getByPublicTenantId(any[TenantId]) returns deletedTenantEntityRead.some.pure[doobie.ConnectionIO]

    def initUserDeletion(): Unit = {
      userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2, user_3)
      userRepository.deleteOp(any[TenantId], any[UserId]) returns (
        user_1.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
        user_2.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
        user_3.asRight[UserRepositoryError].pure[doobie.ConnectionIO]
      )
    }

    def initApiKeyTemplateDeletion(): Unit = {
      apiKeyTemplateRepository
        .getAllForTenantOp(any[TenantId]) returns Stream(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
      apiKeyTemplateRepository.deleteOp(any[TenantId], any[ApiKeyTemplateId]) returns (
        apiKeyTemplate_1.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
        apiKeyTemplate_2.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
        apiKeyTemplate_3.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO]
      )
    }

    def initResourceServerDeletion(): Unit = {
      resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
      resourceServerRepository.deleteOp(any[TenantId], any[ResourceServerId]) returns (
        resourceServer_1.asRight[ResourceServerDbError].pure[doobie.ConnectionIO],
        resourceServer_2.asRight[ResourceServerDbError].pure[doobie.ConnectionIO]
      )
    }

    "everything works correctly" should {

      "call ResourceServerRepository, ApiKeyTemplateRepository, UserRepository and TenantDb" in {
        initVerification()
        initResourceServerDeletion()
        initApiKeyTemplateDeletion()
        initUserDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))

          _ = verify(userRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(userRepository).deleteOp(eqTo(publicTenantId_1), eqTo(user_1.userId))
          _ = verify(userRepository).deleteOp(eqTo(publicTenantId_1), eqTo(user_2.userId))
          _ = verify(userRepository).deleteOp(eqTo(publicTenantId_1), eqTo(user_3.userId))

          _ = verify(apiKeyTemplateRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_1.publicTemplateId))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_2.publicTemplateId))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_3.publicTemplateId))

          _ = verify(resourceServerRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(resourceServerRepository).deleteOp(eqTo(publicTenantId_1), eqTo(resourceServer_1.resourceServerId))
          _ = verify(resourceServerRepository).deleteOp(eqTo(publicTenantId_1), eqTo(resourceServer_2.resourceServerId))

          _ = verify(tenantDb).deleteDeactivated(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing deleted Tenant" in {
        initVerification()
        initResourceServerDeletion()
        initApiKeyTemplateDeletion()
        initUserDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Right(expectedDeletedTenant))
      }
    }

    "TenantDb.getByPublicTenantId returns empty Option" should {

      "NOT call ApiKeyTemplateRepository, UserRepository, ResourceServerRepository.deleteOp or TenantDb.deleteDeactivated" in {
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verifyZeroInteractions(apiKeyTemplateRepository, userRepository)
          _ = verify(resourceServerRepository, times(0)).deleteOp(any[TenantId], any[ResourceServerId])
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return Left containing TenantNotFoundError" in {
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }
    }

    "TenantDb.getByPublicTenantId returns active Tenant" should {

      val activeTenant = tenantEntityRead_1.copy(deactivatedAt = None)

      "NOT call ApiKeyTemplateRepository, UserRepository, ResourceServerRepository.deleteOp or TenantDb.deleteDeactivated" in {
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        tenantDb.getByPublicTenantId(any[TenantId]) returns activeTenant.some.pure[doobie.ConnectionIO]

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verifyZeroInteractions(apiKeyTemplateRepository, userRepository)
          _ = verify(resourceServerRepository, times(0)).deleteOp(any[TenantId], any[ResourceServerId])
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return Left containing TenantIsNotDeactivatedError" in {
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        tenantDb.getByPublicTenantId(any[TenantId]) returns activeTenant.some.pure[doobie.ConnectionIO]

        tenantRepository
          .delete(publicTenantId_1)
          .asserting(_ shouldBe Left(TenantIsNotDeactivatedError(publicTenantId_1)))
      }
    }

    "TenantDb.getByPublicTenantId returns exception" should {

      "NOT call ApiKeyTemplateRepository, UserRepository, ResourceServerRepository.deleteOp or TenantDb.deleteDeactivated" in {
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateRepository, userRepository)
          _ = verify(resourceServerRepository, times(0)).deleteOp(any[TenantId], any[ResourceServerId])
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "UserRepository.getAllForTenantOp returns empty Stream" should {

      "NOT call UserRepository.deleteOp" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        initApiKeyTemplateDeletion()
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(userRepository, times(0)).deleteOp(any[TenantId], any[UserId])
        } yield ()
      }

      "call ApiKeyTemplateRepository, ResourceServerRepository and TenantDb" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        initApiKeyTemplateDeletion()
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(apiKeyTemplateRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_1.publicTemplateId))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_2.publicTemplateId))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_3.publicTemplateId))

          _ = verify(resourceServerRepository).deleteOp(eqTo(publicTenantId_1), eqTo(resourceServer_1.resourceServerId))
          _ = verify(resourceServerRepository).deleteOp(eqTo(publicTenantId_1), eqTo(resourceServer_2.resourceServerId))

          _ = verify(tenantDb).deleteDeactivated(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing deleted Tenant" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        initApiKeyTemplateDeletion()
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Right(expectedDeletedTenant))
      }
    }

    "UserRepository.getAllForTenantOp returns exception in the middle of the Stream" should {

      "NOT call userRepository.deleteOp, ApiKeyTemplateRepository, ResourceServerRepository or TenantDb" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateRepository)
          _ = verify(userRepository, times(0)).deleteOp(any[TenantId], any[UserId])
          _ = verifyZeroInteractions(resourceServerRepository)

          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "UserRepository.deleteOp returns Left containing UserNotFoundError" should {

      val error: UserRepositoryError = UserRepositoryError(UserNotFoundError(publicTenantId_1, publicUserId_3))

      "NOT call ApiKeyTemplateRepository, ResourceServerRepository or TenantDb" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2, user_3)
        userRepository.deleteOp(any[TenantId], any[UserId]) returns (
          user_1.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          user_2.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          error.asLeft[User].pure[doobie.ConnectionIO]
        )

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verifyZeroInteractions(apiKeyTemplateRepository, resourceServerRepository)
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return Left containing CannotDeleteDependencyError" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2, user_3)
        userRepository.deleteOp(any[TenantId], any[UserId]) returns (
          user_1.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          user_2.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          error.asLeft[User].pure[doobie.ConnectionIO]
        )

        tenantRepository
          .delete(publicTenantId_1)
          .asserting(_ shouldBe Left(CannotDeleteDependencyError(publicTenantId_1, error)))
      }
    }

    "UserRepository.deleteOp returns exception" should {

      "NOT call ApiKeyTemplateRepository, ResourceServerRepository or TenantDb" in {
        initVerification()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2, user_3)
        userRepository.deleteOp(any[TenantId], any[UserId]) returns (
          user_1.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          user_2.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[UserRepositoryError, User]]
        )

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateRepository, resourceServerRepository)
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        initVerification()
        initResourceServerDeletion()
        initApiKeyTemplateDeletion()
        userRepository.getAllForTenantOp(any[TenantId]) returns Stream(user_1, user_2, user_3)
        userRepository.deleteOp(any[TenantId], any[UserId]) returns (
          user_1.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          user_2.asRight[UserRepositoryError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[UserRepositoryError, User]]
        )

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateRepository.getAllForTenantOp returns empty Stream" should {

      "NOT call ApiKeyTemplateRepository.deleteOp" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(apiKeyTemplateRepository, times(0)).deleteOp(any[TenantId], any[ApiKeyTemplateId])
        } yield ()
      }

      "call ResourceServerRepository and TenantDb" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(resourceServerRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(resourceServerRepository).deleteOp(eqTo(publicTenantId_1), eqTo(resourceServer_1.resourceServerId))
          _ = verify(resourceServerRepository).deleteOp(eqTo(publicTenantId_1), eqTo(resourceServer_2.resourceServerId))

          _ = verify(tenantDb).deleteDeactivated(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing deleted Tenant" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Right(expectedDeletedTenant))
      }
    }

    "ApiKeyTemplateRepository.getAllForTenantOp returns exception in the middle of the Stream" should {

      "NOT call ApiKeyTemplateRepository.deleteOp, ResourceServerRepository or TenantDb" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository.getAllForTenantOp(any[TenantId]) returns Stream(
          apiKeyTemplate_1,
          apiKeyTemplate_2
        ) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verify(apiKeyTemplateRepository, times(0)).deleteOp(any[TenantId], any[ApiKeyTemplateId])
          _ = verifyZeroInteractions(resourceServerRepository)
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository.getAllForTenantOp(any[TenantId]) returns Stream(
          apiKeyTemplate_1,
          apiKeyTemplate_2
        ) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateRepository.deleteOp returns Left containing ApiKeyTemplateNotFoundError" should {

      val error: ApiKeyTemplateNotFoundError = ApiKeyTemplateNotFoundError(publicTemplateIdStr_3)

      "NOT call ResourceServerRepository or TenantDb" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository
          .getAllForTenantOp(any[TenantId]) returns Stream(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
        apiKeyTemplateRepository.deleteOp(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplate_1.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          apiKeyTemplate_2.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          error.asLeft[ApiKeyTemplate].pure[doobie.ConnectionIO]
        )

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verifyZeroInteractions(resourceServerRepository)
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return Left containing CannotDeleteDependencyError" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository
          .getAllForTenantOp(any[TenantId]) returns Stream(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
        apiKeyTemplateRepository.deleteOp(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplate_1.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          apiKeyTemplate_2.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          error.asLeft[ApiKeyTemplate].pure[doobie.ConnectionIO]
        )

        tenantRepository
          .delete(publicTenantId_1)
          .asserting(_ shouldBe Left(CannotDeleteDependencyError(publicTenantId_1, error)))
      }
    }

    "ApiKeyTemplateRepository.deleteOp returns exception" should {

      "NOT call ResourceServerRepository or TenantDb" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository
          .getAllForTenantOp(any[TenantId]) returns Stream(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
        apiKeyTemplateRepository.deleteOp(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplate_1.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          apiKeyTemplate_2.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]]
        )

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verifyZeroInteractions(resourceServerRepository)
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        initVerification()
        initUserDeletion()
        apiKeyTemplateRepository
          .getAllForTenantOp(any[TenantId]) returns Stream(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
        apiKeyTemplateRepository.deleteOp(any[TenantId], any[ApiKeyTemplateId]) returns (
          apiKeyTemplate_1.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          apiKeyTemplate_2.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[ApiKeyTemplateNotFoundError, ApiKeyTemplate]]
        )

        tenantRepository
          .delete(publicTenantId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ResourceServerRepository.getAllForTenantOp returns empty Stream" should {

      "NOT call ResourceServerRepository.deleteOp" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(resourceServerRepository, times(0)).deleteOp(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "call ApiKeyTemplateRepository, UserRepository and TenantDb.deleteDeactivated" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))

          _ = verify(apiKeyTemplateRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_1.publicTemplateId))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_2.publicTemplateId))
          _ = verify(apiKeyTemplateRepository).deleteOp(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_3.publicTemplateId))

          _ = verify(userRepository).getAllForTenantOp(eqTo(publicTenantId_1))
          _ = verify(userRepository).deleteOp(eqTo(publicTenantId_1), eqTo(user_1.userId))
          _ = verify(userRepository).deleteOp(eqTo(publicTenantId_1), eqTo(user_2.userId))
          _ = verify(userRepository).deleteOp(eqTo(publicTenantId_1), eqTo(user_3.userId))

          _ = verify(resourceServerRepository).getAllForTenantOp(eqTo(publicTenantId_1))

          _ = verify(tenantDb).deleteDeactivated(eqTo(publicTenantId_1))
        } yield ()
      }

      "return Right containing deleted Tenant" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream.empty
        tenantDb.deleteDeactivated(any[TenantId]) returns deletedTenantEntityReadWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Right(expectedDeletedTenant))
      }
    }

    "ResourceServerRepository.getAllForTenantOp returns exception" should {

      "NOT call ResourceServerRepository.deleteOp or TenantDb" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns
          Stream(resourceServer_1) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verify(resourceServerRepository, times(0)).deleteOp(any[TenantId], any[ResourceServerId])
          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception in the middle of the Stream" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns
          Stream(resourceServer_1) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ResourceServerRepository.deleteOp returns Left containing ResourceServerDbError" should {

      val error: ResourceServerDbError = resourceServerNotFoundError(publicResourceServerId_1)

      "NOT call TenantDb" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        resourceServerRepository.deleteOp(any[TenantId], any[ResourceServerId]) returns (
          resourceServer_1.asRight[ResourceServerDbError].pure[doobie.ConnectionIO],
          error.asLeft[ResourceServer].pure[doobie.ConnectionIO]
        )

        for {
          _ <- tenantRepository.delete(publicTenantId_1)

          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return Left containing CannotDeleteDependencyError" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        resourceServerRepository.deleteOp(any[TenantId], any[ResourceServerId]) returns
          error.asLeft[ResourceServer].pure[doobie.ConnectionIO]

        tenantRepository
          .delete(publicTenantId_1)
          .asserting(_ shouldBe Left(CannotDeleteDependencyError(publicTenantId_1, error)))
      }
    }

    "ResourceServerRepository.deleteOp returns exception" should {

      "NOT call TenantDb" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        resourceServerRepository.deleteOp(any[TenantId], any[ResourceServerId]) returns (
          resourceServer_1.asRight[ResourceServerDbError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[ResourceServerDbError, ResourceServer]]
        )

        for {
          _ <- tenantRepository.delete(publicTenantId_1).attempt

          _ = verify(tenantDb, times(0)).deleteDeactivated(any[TenantId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        resourceServerRepository.getAllForTenantOp(any[TenantId]) returns Stream(resourceServer_1, resourceServer_2)
        resourceServerRepository.deleteOp(any[TenantId], any[ResourceServerId]) returns (
          resourceServer_1.asRight[ResourceServerDbError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[ResourceServerDbError, ResourceServer]]
        )

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "TenantDb.deleteDeactivated returns Left containing TenantNotFoundError" should {
      "return Left containing this error" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns tenantNotFoundWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Left(tenantNotFoundError))
      }
    }

    "TenantDb.deleteDeactivated returns Left containing TenantNotDeactivatedError" should {
      "return Left containing this error" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns tenantIsNotDeactivatedErrorWrapped

        tenantRepository.delete(publicTenantId_1).asserting(_ shouldBe Left(tenantIsNotDeactivatedError))
      }
    }

    "TenantDb.deleteDeactivated returns different exception" should {
      "return failed IO containing this exception" in {
        initVerification()
        initUserDeletion()
        initApiKeyTemplateDeletion()
        initResourceServerDeletion()
        tenantDb.deleteDeactivated(any[TenantId]) returns testExceptionWrappedE[TenantDbError]

        tenantRepository.delete(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
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

}
