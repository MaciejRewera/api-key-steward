package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.errors.PermissionDbError.PermissionInsertionError.PermissionInsertionErrorImpl
import apikeysteward.model.errors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.errors.ResourceServerDbError
import apikeysteward.model.errors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.errors.ResourceServerDbError._
import apikeysteward.model.ResourceServer
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.{PermissionEntity, ResourceServerEntity, TenantEntity}
import apikeysteward.repositories.db.{PermissionDb, ResourceServerDb, TenantDb}
import apikeysteward.services.UuidGenerator
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

class ResourceServerRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val uuidGenerator = mock[UuidGenerator]
  private val tenantDb = mock[TenantDb]
  private val resourceServerDb = mock[ResourceServerDb]
  private val permissionDb = mock[PermissionDb]
  private val permissionRepository = mock[PermissionRepository]

  private val resourceServerRepository =
    new ResourceServerRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)(
      noopTransactor
    )

  override def beforeEach(): Unit =
    reset(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)

  private val resourceServerNotFoundError = ResourceServerNotFoundError(publicResourceServerIdStr_1)
  private val resourceServerNotFoundErrorWrapped =
    resourceServerNotFoundError.asLeft[ResourceServerEntity.Read].pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, ResourceServerEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, ResourceServerEntity.Read]]

  "ResourceServerRepository on insert" when {

    val tenantEntityReadWrapped = Option(tenantEntityRead_1).pure[doobie.ConnectionIO]

    val resourceServerEntityReadWrapped =
      resourceServerEntityRead_1.asRight[ResourceServerInsertionError].pure[doobie.ConnectionIO]

    val permissionEntityReadWrapped = permissionEntityRead_1.asRight[PermissionInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")

    val resourceServerInsertionError: ResourceServerInsertionError = ResourceServerInsertionErrorImpl(testSqlException)
    val resourceServerInsertionErrorWrapped =
      resourceServerInsertionError.asLeft[ResourceServerEntity.Read].pure[doobie.ConnectionIO]

    val permissionInsertionError: PermissionInsertionError = PermissionInsertionErrorImpl(testSqlException)
    val permissionInsertionErrorWrapped =
      permissionInsertionError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UuidGenerator, TenantDb, ResourceServerDb and PermissionDb" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb.insert(any[ResourceServerEntity.Write]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        for {
          _ <- resourceServerRepository.insert(publicTenantId_1, resourceServer_1)

          _ = verify(uuidGenerator, times(2)).generateUuid
          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
          _ = verify(resourceServerDb).insert(eqTo(resourceServerEntityWrite_1))
          _ = verify(permissionDb).insert(eqTo(permissionEntityWrite_1))
        } yield ()
      }

      "return Right containing ResourceServer" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb.insert(any[ResourceServerEntity.Write]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .asserting(_ shouldBe Right(resourceServer_1))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantDb, ResourceServerDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- resourceServerRepository.insert(publicTenantId_1, resourceServer_1).attempt

          _ = verifyZeroInteractions(tenantDb, resourceServerDb, permissionDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
    "TenantDb returns empty Option" should {

      "NOT call either ResourceServerDb or PermissionDb" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- resourceServerRepository.insert(publicTenantId_1, resourceServer_1)

          _ = verifyZeroInteractions(resourceServerDb, permissionDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call either ResourceServerDb or PermissionDb" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- resourceServerRepository.insert(publicTenantId_1, resourceServer_1).attempt

          _ = verifyZeroInteractions(resourceServerDb, permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ResourceServerDb returns Left containing ResourceServerInsertionError" should {

      "NOT call PermissionDb" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb.insert(any[ResourceServerEntity.Write]) returns resourceServerInsertionErrorWrapped

        for {
          _ <- resourceServerRepository.insert(publicTenantId_1, resourceServer_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb.insert(any[ResourceServerEntity.Write]) returns resourceServerInsertionErrorWrapped

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .asserting(_ shouldBe Left(resourceServerInsertionError))
      }
    }

    "ResourceServerDb returns different exception" should {

      "NOT call PermissionDb" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb
          .insert(any[ResourceServerEntity.Write]) returns testExceptionWrappedE[ResourceServerInsertionError]

        for {
          _ <- resourceServerRepository.insert(publicTenantId_1, resourceServer_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb
          .insert(any[ResourceServerEntity.Write]) returns testExceptionWrappedE[ResourceServerInsertionError]

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb.insert returns PermissionInsertionError" should {
      "return Left containing CannotInsertPermissionError" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb.insert(any[ResourceServerEntity.Write]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionInsertionErrorWrapped

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .asserting(_ shouldBe Left(CannotInsertPermissionError(publicResourceServerId_1, permissionInsertionError)))
      }
    }

    "PermissionDb.insert returns different exception" should {
      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns (IO.pure(resourceServerDbId_1), IO.pure(permissionDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        resourceServerDb.insert(any[ResourceServerEntity.Write]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[PermissionInsertionError, PermissionEntity.Read]]

        resourceServerRepository
          .insert(publicTenantId_1, resourceServer_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerRepository on update" when {

    val updatedResourceServerEntityReadWrapped =
      resourceServerEntityRead_1
        .copy(name = resourceServerNameUpdated, description = resourceServerDescriptionUpdated)
        .asRight[ResourceServerNotFoundError]
        .pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ResourceServerDb and PermissionDb" in {
        resourceServerDb.update(
          any[TenantId],
          any[ResourceServerEntity.Update]
        ) returns updatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        for {
          _ <- resourceServerRepository.update(publicTenantId_1, resourceServerUpdate_1)

          _ = verify(resourceServerDb).update(eqTo(publicTenantId_1), eqTo(resourceServerEntityUpdate_1))
          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )(eqTo(none[String]))
        } yield ()
      }

      "return Right containing updated ResourceServer" in {
        resourceServerDb.update(
          any[TenantId],
          any[ResourceServerEntity.Update]
        ) returns updatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        val expectedUpdatedResourceServer =
          resourceServer_1.copy(name = resourceServerNameUpdated, description = resourceServerDescriptionUpdated)

        resourceServerRepository
          .update(publicTenantId_1, resourceServerUpdate_1)
          .asserting(_ shouldBe Right(expectedUpdatedResourceServer))
      }
    }

    "ResourceServerDb returns Left containing ResourceServerNotFoundError" should {

      "NOT call PermissionDb" in {
        resourceServerDb.update(
          any[TenantId],
          any[ResourceServerEntity.Update]
        ) returns resourceServerNotFoundErrorWrapped

        for {
          _ <- resourceServerRepository.update(publicTenantId_1, resourceServerUpdate_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        resourceServerDb.update(
          any[TenantId],
          any[ResourceServerEntity.Update]
        ) returns resourceServerNotFoundErrorWrapped

        resourceServerRepository
          .update(publicTenantId_1, resourceServerUpdate_1)
          .asserting(_ shouldBe Left(resourceServerNotFoundError))
      }
    }

    "ResourceServerDb returns different exception" should {

      "NOT call PermissionDb" in {
        resourceServerDb
          .update(any[TenantId], any[ResourceServerEntity.Update]) returns testExceptionWrappedE[
          ResourceServerNotFoundError
        ]

        for {
          _ <- resourceServerRepository.update(publicTenantId_1, resourceServerUpdate_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb
          .update(any[TenantId], any[ResourceServerEntity.Update]) returns testExceptionWrappedE[
          ResourceServerNotFoundError
        ]

        resourceServerRepository
          .update(publicTenantId_1, resourceServerUpdate_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns an exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.update(
          any[TenantId],
          any[ResourceServerEntity.Update]
        ) returns updatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository
          .update(publicTenantId_1, resourceServerUpdate_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerRepository on activate" when {

    val activatedResourceServerEntityRead = resourceServerEntityRead_1.copy(deactivatedAt = None)
    val activatedResourceServerEntityReadWrapped =
      activatedResourceServerEntityRead.asRight[ResourceServerNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ResourceServerDb and PermissionDb" in {
        resourceServerDb.activate(any[TenantId], any[ResourceServerId]) returns activatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        for {
          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)

          _ = verify(resourceServerDb).activate(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))
          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )(eqTo(none[String]))
        } yield ()
      }

      "return Right containing activated ResourceServer" in {
        resourceServerDb.activate(any[TenantId], any[ResourceServerId]) returns activatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        val expectedActivatedResourceServer = resourceServer_1.copy(isActive = true)

        resourceServerRepository
          .activate(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Right(expectedActivatedResourceServer))
      }
    }

    "ResourceServerDb returns Left containing ResourceServerNotFoundError" should {

      "NOT call PermissionDb" in {
        resourceServerDb.activate(any[TenantId], any[ResourceServerId]) returns resourceServerNotFoundErrorWrapped

        for {
          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        resourceServerDb.activate(any[TenantId], any[ResourceServerId]) returns resourceServerNotFoundErrorWrapped

        resourceServerRepository
          .activate(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(resourceServerNotFoundError))
      }
    }

    "ResourceServerDb returns different exception" should {

      "NOT call PermissionDb" in {
        resourceServerDb
          .activate(any[TenantId], any[ResourceServerId]) returns testExceptionWrappedE[ResourceServerNotFoundError]

        for {
          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb
          .activate(any[TenantId], any[ResourceServerId]) returns testExceptionWrappedE[ResourceServerNotFoundError]

        resourceServerRepository
          .activate(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns an exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.activate(any[TenantId], any[ResourceServerId]) returns activatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository
          .activate(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerRepository on deactivate" when {

    val deactivatedResourceServerEntityRead = resourceServerEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deactivatedResourceServerEntityReadWrapped =
      deactivatedResourceServerEntityRead.asRight[ResourceServerNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ResourceServerDb and PermissionDb" in {
        resourceServerDb.deactivate(
          any[TenantId],
          any[ResourceServerId]
        ) returns deactivatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        for {
          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)

          _ = verify(resourceServerDb).deactivate(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))
          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )(eqTo(none[String]))
        } yield ()
      }

      "return Right containing deactivated ResourceServer" in {
        resourceServerDb.deactivate(
          any[TenantId],
          any[ResourceServerId]
        ) returns deactivatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        val expectedDeactivatedResourceServer = resourceServer_1.copy(isActive = false)

        resourceServerRepository
          .deactivate(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Right(expectedDeactivatedResourceServer))
      }
    }

    "ResourceServerDb returns Left containing ResourceServerNotFoundError" should {

      "NOT call PermissionDb" in {
        resourceServerDb.deactivate(any[TenantId], any[ResourceServerId]) returns resourceServerNotFoundErrorWrapped

        for {
          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        resourceServerDb.deactivate(any[TenantId], any[ResourceServerId]) returns resourceServerNotFoundErrorWrapped

        resourceServerRepository
          .deactivate(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(resourceServerNotFoundError))
      }
    }

    "ResourceServerDb returns different exception" should {

      "NOT call PermissionDb" in {
        resourceServerDb
          .deactivate(any[TenantId], any[ResourceServerId]) returns testExceptionWrappedE[ResourceServerNotFoundError]

        for {
          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb
          .deactivate(any[TenantId], any[ResourceServerId]) returns testExceptionWrappedE[ResourceServerNotFoundError]

        resourceServerRepository
          .deactivate(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns an exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.deactivate(
          any[TenantId],
          any[ResourceServerId]
        ) returns deactivatedResourceServerEntityReadWrapped
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository
          .deactivate(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerRepository on delete" when {

    val deletedResourceServerEntityRead = resourceServerEntityRead_1.copy(deactivatedAt = Some(nowInstant))
    val deletedResourceServerEntityReadWrapped =
      deletedResourceServerEntityRead.asRight[ResourceServerDbError].pure[doobie.ConnectionIO]

    val resourceServerNotFound = resourceServerNotFoundError.asInstanceOf[ResourceServerDbError]
    val resourceServerNotFoundWrapped =
      resourceServerNotFound.asLeft[ResourceServerEntity.Read].pure[doobie.ConnectionIO]

    val resourceServerIsNotDeactivatedError: ResourceServerDbError =
      ResourceServerIsNotDeactivatedError(publicResourceServerId_1)
    val resourceServerIsNotDeactivatedErrorWrapped =
      resourceServerIsNotDeactivatedError.asLeft[ResourceServerEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ResourceServerDb, PermissionDb and PermissionRepository" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[TenantId], any[ResourceServerId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_2.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_3.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO]
        )
        resourceServerDb.deleteDeactivated(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityReadWrapped

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1)

          _ = verify(resourceServerDb).getByPublicResourceServerId(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )
          _ = verify(permissionDb).getAllBy(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))(eqTo(none[String]))
          _ = verify(permissionRepository).deleteOp(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1),
            eqTo(publicPermissionId_1)
          )
          _ = verify(permissionRepository).deleteOp(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1),
            eqTo(publicPermissionId_2)
          )
          _ = verify(permissionRepository).deleteOp(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1),
            eqTo(publicPermissionId_3)
          )
          _ = verify(resourceServerDb).deleteDeactivated(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))
        } yield ()
      }

      "return Right containing deleted ResourceServer" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some.pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[TenantId], any[ResourceServerId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_2.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionEntityRead_3.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO]
        )
        resourceServerDb.deleteDeactivated(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityReadWrapped

        val expectedDeletedResourceServer =
          resourceServer_1.copy(isActive = false, permissions = List(permission_1, permission_2, permission_3))

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Right(expectedDeletedResourceServer))
      }
    }

    "ResourceServerDb.getByPublicResourceServerId returns empty Option" should {

      "NOT call ResourceServerDb.deleteDeactivated, PermissionDb or PermissionRepository" in {
        resourceServerDb
          .getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1)

          _ = verifyZeroInteractions(permissionDb, permissionRepository)
          _ = verify(resourceServerDb, times(0)).deleteDeactivated(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }
    }

    "ResourceServerDb.getByPublicResourceServerId returns Option containing active ResourceServer" should {

      val activeResourceServer = resourceServerEntityRead_1.copy(deactivatedAt = None)

      "NOT call ResourceServerDb.deleteDeactivated, PermissionDb or PermissionRepository" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns activeResourceServer.some.pure[doobie.ConnectionIO]

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1)

          _ = verifyZeroInteractions(permissionDb, permissionRepository)
          _ = verify(resourceServerDb, times(0)).deleteDeactivated(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "return Left containing ResourceServerIsNotDeactivatedError" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns activeResourceServer.some.pure[doobie.ConnectionIO]

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(ResourceServerIsNotDeactivatedError(publicResourceServerId_1)))
      }
    }

    "ResourceServerDb.getByPublicResourceServerId returns exception" should {

      "NOT call ResourceServerDb.deleteDeactivated, PermissionDb or PermissionRepository" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1).attempt

          _ = verifyZeroInteractions(permissionDb, permissionRepository)
          _ = verify(resourceServerDb, times(0)).deleteDeactivated(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb.getAllBy returns an exception" should {

      "NOT call either PermissionDb.delete or ResourceServerDb.deleteDeactivated" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1).attempt

          _ = verify(permissionDb, times(0)).delete(any[TenantId], any[ResourceServerId], any[PermissionId])
          _ = verify(resourceServerDb, times(0)).deleteDeactivated(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++
          Stream.raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionRepository.deleteOp returns PermissionNotFoundError" should {

      val permissionNotFoundError = PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)

      "NOT call ResourceServerDb.deleteDeactivated" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[TenantId], any[ResourceServerId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionNotFoundError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]
        )

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1)
          _ = verify(resourceServerDb, times(0)).deleteDeactivated(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "return Left containing CannotDeletePermissionError" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[TenantId], any[ResourceServerId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          permissionNotFoundError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]
        )

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(CannotDeletePermissionError(publicResourceServerId_1, permissionNotFoundError)))
      }
    }

    "PermissionRepository.deleteOp returns different exception" should {

      "NOT call ResourceServerDb.deleteDeactivated" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[TenantId], any[ResourceServerId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[PermissionNotFoundError, PermissionEntity.Read]]
        )

        for {
          _ <- resourceServerRepository.delete(publicTenantId_1, publicResourceServerId_1).attempt

          _ = verify(resourceServerDb, times(0)).deleteDeactivated(any[TenantId], any[ResourceServerId])
        } yield ()
      }

      "return Left containing CannotDeletePermissionError" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )
        permissionRepository.deleteOp(any[TenantId], any[ResourceServerId], any[PermissionId]) returns (
          permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Either[PermissionNotFoundError, PermissionEntity.Read]]
        )

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ResourceServerDb.deleteDeactivated returns Left containing ResourceServerNotFoundError" should {
      "return Left containing this error" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )
        permissionRepository.deleteOp(
          any[TenantId],
          any[ResourceServerId],
          any[PermissionId]
        ) returns permissionEntityRead_1
          .asRight[PermissionNotFoundError]
          .pure[doobie.ConnectionIO]
        resourceServerDb.deleteDeactivated(any[TenantId], any[ResourceServerId]) returns resourceServerNotFoundWrapped

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(resourceServerNotFoundError))
      }
    }

    "ResourceServerDb.deleteDeactivated returns Left containing ResourceServerNotDeactivatedError" should {
      "return Left containing this error" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )
        permissionRepository.deleteOp(
          any[TenantId],
          any[ResourceServerId],
          any[PermissionId]
        ) returns permissionEntityRead_1
          .asRight[PermissionNotFoundError]
          .pure[doobie.ConnectionIO]
        resourceServerDb.deleteDeactivated(
          any[TenantId],
          any[ResourceServerId]
        ) returns resourceServerIsNotDeactivatedErrorWrapped

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Left(resourceServerIsNotDeactivatedError))
      }
    }

    "ResourceServerDb.deleteDeactivated returns different exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns deletedResourceServerEntityRead.some
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )
        permissionRepository.deleteOp(
          any[TenantId],
          any[ResourceServerId],
          any[PermissionId]
        ) returns permissionEntityRead_1
          .asRight[PermissionNotFoundError]
          .pure[doobie.ConnectionIO]
        resourceServerDb
          .deleteDeactivated(any[TenantId], any[ResourceServerId]) returns testExceptionWrappedE[ResourceServerDbError]

        resourceServerRepository
          .delete(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerRepository on getBy(:resourceServerId)" when {

    "should always call ResourceServerDb" in {
      resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns Option(
        resourceServerEntityRead_1
      )
        .pure[doobie.ConnectionIO]
      permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream.empty

      for {
        _ <- resourceServerRepository.getBy(publicTenantId_1, publicResourceServerId_1)

        _ = verify(resourceServerDb).getByPublicResourceServerId(
          eqTo(publicTenantId_1),
          eqTo(publicResourceServerId_1)
        )
      } yield ()
    }

    "ResourceServerDb returns empty Option" should {

      "NOT call PermissionDb" in {
        resourceServerDb
          .getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- resourceServerRepository.getBy(publicTenantId_1, publicResourceServerId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty Option" in {
        resourceServerDb
          .getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        resourceServerRepository.getBy(publicTenantId_1, publicResourceServerId_1).asserting(_ shouldBe None)
      }
    }

    "ResourceServerDb returns Option containing ResourceServerEntity" should {

      "call PermissionDb with publicResourceServerId" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns Option(
          resourceServerEntityRead_1
        )
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        for {
          _ <- resourceServerRepository.getBy(publicTenantId_1, publicResourceServerId_1)
          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )(eqTo(none[String]))
        } yield ()
      }

      "return Option containing ResourceServer" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns Option(
          resourceServerEntityRead_1
        )
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        )

        resourceServerRepository
          .getBy(publicTenantId_1, publicResourceServerId_1)
          .asserting(_ shouldBe Some(resourceServer_1))
      }
    }

    "ResourceServerDb returns exception" should {

      "NOT call PermissionDb" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        for {
          _ <- resourceServerRepository.getBy(publicTenantId_1, publicResourceServerId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        resourceServerRepository
          .getBy(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns Option(
          resourceServerEntityRead_1
        )
          .pure[doobie.ConnectionIO]
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository
          .getBy(publicTenantId_1, publicResourceServerId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ResourceServerRepository on getAllForTenant" when {

    "should always call ResourceServerDb" in {
      resourceServerDb.getAllForTenant(any[TenantId]) returns Stream.empty

      for {
        _ <- resourceServerRepository.getAllForTenant(publicTenantId_1)

        _ = verify(resourceServerDb).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "ResourceServerDb returns empty Stream" should {

      "NOT call PermissionDb" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream.empty

        for {
          _ <- resourceServerRepository.getAllForTenant(publicTenantId_1)
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty List" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream.empty

        resourceServerRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[ResourceServer])
      }
    }

    "ResourceServerDb returns ResourceServerEntities in Stream" should {

      "call PermissionDb with every publicResourceServerId" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream(
          resourceServerEntityRead_1,
          resourceServerEntityRead_2,
          resourceServerEntityRead_3
        )
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        for {
          _ <- resourceServerRepository.getAllForTenant(publicTenantId_1)

          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )(eqTo(none[String]))
          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_2)
          )(eqTo(none[String]))
          _ = verify(permissionDb).getAllBy(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_3)
          )(eqTo(none[String]))
        } yield ()
      }

      "return List containing ResourceServers" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream(
          resourceServerEntityRead_1,
          resourceServerEntityRead_2,
          resourceServerEntityRead_3
        )
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        resourceServerRepository
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(resourceServer_1, resourceServer_2, resourceServer_3))
      }
    }

    "ResourceServerDb returns exception" should {

      "NOT call PermissionDb" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- resourceServerRepository.getAllForTenant(publicTenantId_1).attempt
          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.getAllForTenant(any[TenantId]) returns Stream(resourceServerEntityRead_1)
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        resourceServerRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
