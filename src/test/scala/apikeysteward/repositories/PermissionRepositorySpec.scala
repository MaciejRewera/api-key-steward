package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantEntityRead_1}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.errors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.errors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.db.entity.{PermissionEntity, ResourceServerEntity, TenantEntity}
import apikeysteward.repositories.db.{ApiKeyTemplatesPermissionsDb, PermissionDb, ResourceServerDb, TenantDb}
import apikeysteward.services.UuidGenerator
import cats.effect.IO
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

class PermissionRepositorySpec
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
  private val apiKeyTemplatesPermissionsDb = mock[ApiKeyTemplatesPermissionsDb]

  private val permissionRepository =
    new PermissionRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, apiKeyTemplatesPermissionsDb)(
      noopTransactor
    )

  override def beforeEach(): Unit =
    reset(uuidGenerator, tenantDb, resourceServerDb, permissionDb, apiKeyTemplatesPermissionsDb)

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, PermissionEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, PermissionEntity.Read]]

  "PermissionRepository on insert" when {

    val resourceServerEntityReadWrapped = Option(resourceServerEntityRead_1).pure[doobie.ConnectionIO]

    val permissionEntityReadWrapped = permissionEntityRead_1.asRight[PermissionInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")
    val permissionInsertionError: PermissionInsertionError = PermissionInsertionErrorImpl(testSqlException)
    val permissionInsertionErrorWrapped =
      permissionInsertionError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UuidGenerator, ResourceServerDb and PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        for {
          _ <- permissionRepository.insert(publicTenantId_1, publicResourceServerId_1, permission_1)

          _ = verify(uuidGenerator).generateUuid
          _ = verify(resourceServerDb).getByPublicResourceServerId(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1)
          )
          _ = verify(permissionDb).insert(eqTo(permissionEntityWrite_1))
        } yield ()
      }

      "return Right containing Permission" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .asserting(_ shouldBe Right(permission_1))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantDb, ResourceServerDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- permissionRepository.insert(publicTenantId_1, publicResourceServerId_1, permission_1).attempt

          _ = verifyZeroInteractions(tenantDb, resourceServerDb, permissionDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call ResourceServerDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- permissionRepository.insert(publicTenantId_1, publicResourceServerId_1, permission_1)

          _ = verifyZeroInteractions(resourceServerDb, permissionDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call ResourceServerDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- permissionRepository.insert(publicTenantId_1, publicResourceServerId_1, permission_1).attempt

          _ = verifyZeroInteractions(resourceServerDb, permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ResourceServerDb returns empty Option" should {

      "NOT call PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb
          .getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- permissionRepository.insert(publicTenantId_1, publicResourceServerId_1, permission_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing ReferencedResourceServerDoesNotExistError" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb
          .getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .asserting(_ shouldBe Left(ReferencedResourceServerDoesNotExistError(publicResourceServerId_1)))
      }
    }

    "ResourceServerDb returns exception" should {

      "NOT call PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        for {
          _ <- permissionRepository.insert(publicTenantId_1, publicResourceServerId_1, permission_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb.getByPublicResourceServerId(any[TenantId], any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns Left containing PermissionInsertionError" should {
      "return Left containing this error" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionInsertionErrorWrapped

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .asserting(_ shouldBe Left(permissionInsertionError))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(permissionDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns Option(tenantEntityRead_1).pure[doobie.ConnectionIO]
        resourceServerDb.getByPublicResourceServerId(
          any[TenantId],
          any[ResourceServerId]
        ) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns testExceptionWrappedE[PermissionInsertionError]

        permissionRepository
          .insert(publicTenantId_1, publicResourceServerId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on delete" when {

    val deletedPermissionEntityReadWrapped =
      permissionEntityRead_1.asRight[PermissionNotFoundError].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsDb and PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[TenantId], any[PermissionId]) returns 3
          .pure[doobie.ConnectionIO]
        permissionDb.delete(
          any[TenantId],
          any[ResourceServerId],
          any[PermissionId]
        ) returns deletedPermissionEntityReadWrapped

        for {
          _ <- permissionRepository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)

          _ = verify(apiKeyTemplatesPermissionsDb).deleteAllForPermission(
            eqTo(publicTenantId_1),
            eqTo(publicPermissionId_1)
          )
          _ = verify(permissionDb).delete(
            eqTo(publicTenantId_1),
            eqTo(publicResourceServerId_1),
            eqTo(publicPermissionId_1)
          )
        } yield ()
      }

      "return Right containing deleted Permission" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[TenantId], any[PermissionId]) returns 3
          .pure[doobie.ConnectionIO]
        permissionDb.delete(
          any[TenantId],
          any[ResourceServerId],
          any[PermissionId]
        ) returns deletedPermissionEntityReadWrapped

        permissionRepository
          .delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Right(permission_1))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[TenantId], any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- permissionRepository.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[TenantId], any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        permissionRepository
          .delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns Left containing PermissionNotFoundError" should {
      "return Left containing this error" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[TenantId], any[PermissionId]) returns 3
          .pure[doobie.ConnectionIO]
        val permissionNotFoundError = PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)
        permissionDb.delete(any[TenantId], any[ResourceServerId], any[PermissionId]) returns permissionNotFoundError
          .asLeft[PermissionEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Left(permissionNotFoundError))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[TenantId], any[PermissionId]) returns 3
          .pure[doobie.ConnectionIO]
        permissionDb
          .delete(any[TenantId], any[ResourceServerId], any[PermissionId]) returns testExceptionWrappedE[
          PermissionNotFoundError
        ]

        permissionRepository
          .delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getBy(:publicTenantId, :publicResourceServerId, :publicPermissionId)" when {

    "should always call PermissionDb" in {
      permissionDb.getBy(any[TenantId], any[ResourceServerId], any[PermissionId]) returns Option(permissionEntityRead_1)
        .pure[doobie.ConnectionIO]

      for {
        _ <- permissionRepository.getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)

        _ = verify(permissionDb).getBy(
          eqTo(publicTenantId_1),
          eqTo(publicResourceServerId_1),
          eqTo(publicPermissionId_1)
        )
      } yield ()
    }

    "PermissionDb returns empty Option" should {
      "return empty Option" in {
        permissionDb.getBy(any[TenantId], any[ResourceServerId], any[PermissionId]) returns Option
          .empty[PermissionEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe None)
      }
    }

    "PermissionDb returns Option containing PermissionEntity" should {
      "return Option containing Permission" in {
        permissionDb.getBy(any[TenantId], any[ResourceServerId], any[PermissionId]) returns Option(
          permissionEntityRead_1
        )
          .pure[doobie.ConnectionIO]

        permissionRepository
          .getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Some(permission_1))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getBy(any[TenantId], any[ResourceServerId], any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]]

        permissionRepository
          .getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getBy(:publicTenantId, :publicPermissionIds)" when {

    "should always call PermissionDb" in {
      permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns
        Option(permissionEntityRead_1).pure[doobie.ConnectionIO]

      for {
        _ <- permissionRepository.getBy(publicTenantId_1, List(publicPermissionId_1, publicPermissionId_2))

        _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicTenantId_1), eqTo(publicPermissionId_1))
        _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicTenantId_1), eqTo(publicPermissionId_2))
      } yield ()
    }

    "PermissionDb returns Option containing PermissionEntities for all calls" should {
      "return Right containing all these Permissions" in {
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns (
          Option(permissionEntityRead_1).pure[doobie.ConnectionIO],
          Option(permissionEntityRead_2).pure[doobie.ConnectionIO]
        )

        permissionRepository
          .getBy(publicTenantId_1, List(publicPermissionId_1, publicPermissionId_2))
          .asserting(_ shouldBe Right(List(permission_1, permission_2)))
      }
    }

    "PermissionDb returns empty Option for one of the calls" should {
      "return Left containing PermissionNotFoundError" in {
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns (
          Option(permissionEntityRead_1).pure[doobie.ConnectionIO],
          Option.empty[PermissionEntity.Read].pure[doobie.ConnectionIO]
        )

        permissionRepository
          .getBy(publicTenantId_1, List(publicPermissionId_1, publicPermissionId_2))
          .asserting(_ shouldBe Left(PermissionNotFoundError.forTenant(publicTenantId_1, publicPermissionId_2)))
      }
    }

    "PermissionDb returns exception for one of the calls" should {
      "return failed IO containing this exception" in {
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns (
          Option(permissionEntityRead_1).pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]]
        )

        permissionRepository
          .getBy(publicTenantId_1, List(publicPermissionId_1, publicPermissionId_2))
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getAllPermissionsForTemplate" when {

    "should always call PermissionDb" in {
      permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.empty

      for {
        _ <- permissionRepository.getAllFor(publicTenantId_1, publicTemplateId_1)

        _ = verify(permissionDb).getAllForTemplate(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
      } yield ()
    }

    "PermissionDb returns empty Stream" should {
      "return empty List" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.empty

        permissionRepository
          .getAllFor(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe List.empty[Permission])
      }
    }

    "PermissionDb returns PermissionEntities in Stream" should {
      "return List containing Permissions" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )

        permissionRepository
          .getAllFor(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream
          .raiseError[doobie.ConnectionIO](
            testException
          )

        permissionRepository
          .getAllFor(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getAllBy" when {

    val nameFragment = Some("test:name:fragment")

    "should always call PermissionDb" in {
      permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream.empty

      for {
        _ <- permissionRepository.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment)

        _ = verify(permissionDb).getAllBy(eqTo(publicTenantId_1), eqTo(publicResourceServerId_1))(eqTo(nameFragment))
      } yield ()
    }

    "PermissionDb returns empty Stream" should {
      "return empty List" in {
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream.empty

        permissionRepository
          .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment)
          .asserting(_ shouldBe List.empty[Permission])
      }
    }

    "PermissionDb returns PermissionEntities in Stream" should {
      "return List containing Permissions" in {
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )

        permissionRepository
          .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllBy(any[TenantId], any[ResourceServerId])(any[Option[String]]) returns Stream
          .raiseError[doobie.ConnectionIO](
            testException
          )

        permissionRepository
          .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
