package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.ResourceServersTestData.{resourceServerEntityRead_1, publicResourceServerId_1}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.repositories.db.entity.{ResourceServerEntity, PermissionEntity}
import apikeysteward.repositories.db.{ApiKeyTemplatesPermissionsDb, ResourceServerDb, PermissionDb}
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

  private val resourceServerDb = mock[ResourceServerDb]
  private val permissionDb = mock[PermissionDb]
  private val apiKeyTemplatesPermissionsDb = mock[ApiKeyTemplatesPermissionsDb]

  private val permissionRepository =
    new PermissionRepository(resourceServerDb, permissionDb, apiKeyTemplatesPermissionsDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(resourceServerDb, permissionDb, apiKeyTemplatesPermissionsDb)

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, PermissionEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, PermissionEntity.Read]]

  "PermissionRepository on insert" when {

    val resourceServerId = 13L
    val resourceServerEntityReadWrapped =
      Option(resourceServerEntityRead_1.copy(id = resourceServerId)).pure[doobie.ConnectionIO]

    val permissionEntityReadWrapped = permissionEntityRead_1.asRight[PermissionInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")
    val permissionInsertionError: PermissionInsertionError = PermissionInsertionErrorImpl(testSqlException)
    val permissionInsertionErrorWrapped =
      permissionInsertionError.asLeft[PermissionEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ResourceServerDb and PermissionDb" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        for {
          _ <- permissionRepository.insert(publicResourceServerId_1, permission_1)

          _ = verify(resourceServerDb).getByPublicResourceServerId(eqTo(publicResourceServerId_1))
          expectedPermissionEntityWrite = PermissionEntity.Write(
            resourceServerId = resourceServerId,
            publicPermissionId = publicPermissionIdStr_1,
            name = permissionName_1,
            description = permissionDescription_1
          )
          _ = verify(permissionDb).insert(eqTo(expectedPermissionEntityWrite))
        } yield ()
      }

      "return Right containing Permission" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionEntityReadWrapped

        permissionRepository.insert(publicResourceServerId_1, permission_1).asserting(_ shouldBe Right(permission_1))
      }
    }

    "ResourceServerDb returns empty Option" should {

      "NOT call PermissionDb" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- permissionRepository.insert(publicResourceServerId_1, permission_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing ReferencedResourceServerDoesNotExistError" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns none[ResourceServerEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .insert(publicResourceServerId_1, permission_1)
          .asserting(_ shouldBe Left(ReferencedResourceServerDoesNotExistError(publicResourceServerId_1)))
      }
    }

    "ResourceServerDb returns exception" should {

      "NOT call PermissionDb" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        for {
          _ <- permissionRepository.insert(publicResourceServerId_1, permission_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ResourceServerEntity.Read]]

        permissionRepository
          .insert(publicResourceServerId_1, permission_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns Left containing PermissionInsertionError" should {
      "return Left containing this error" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns permissionInsertionErrorWrapped

        permissionRepository
          .insert(publicResourceServerId_1, permission_1)
          .asserting(_ shouldBe Left(permissionInsertionError))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        resourceServerDb.getByPublicResourceServerId(any[ResourceServerId]) returns resourceServerEntityReadWrapped
        permissionDb.insert(any[PermissionEntity.Write]) returns testExceptionWrappedE[PermissionInsertionError]

        permissionRepository
          .insert(publicResourceServerId_1, permission_1)
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
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[PermissionId]) returns 3.pure[doobie.ConnectionIO]
        permissionDb.delete(any[ResourceServerId], any[PermissionId]) returns deletedPermissionEntityReadWrapped

        for {
          _ <- permissionRepository.delete(publicResourceServerId_1, publicPermissionId_1)

          _ = verify(apiKeyTemplatesPermissionsDb).deleteAllForPermission(eqTo(publicPermissionId_1))
          _ = verify(permissionDb).delete(eqTo(publicResourceServerId_1), eqTo(publicPermissionId_1))
        } yield ()
      }

      "return Right containing deleted Permission" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[PermissionId]) returns 3.pure[doobie.ConnectionIO]
        permissionDb.delete(any[ResourceServerId], any[PermissionId]) returns deletedPermissionEntityReadWrapped

        permissionRepository
          .delete(publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Right(permission_1))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- permissionRepository.delete(publicResourceServerId_1, publicPermissionId_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        permissionRepository
          .delete(publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns Left containing PermissionNotFoundError" should {
      "return Left containing this error" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[PermissionId]) returns 3.pure[doobie.ConnectionIO]
        val permissionNotFoundError = PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)
        permissionDb.delete(any[ResourceServerId], any[PermissionId]) returns permissionNotFoundError
          .asLeft[PermissionEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository
          .delete(publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Left(permissionNotFoundError))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForPermission(any[PermissionId]) returns 3.pure[doobie.ConnectionIO]
        permissionDb
          .delete(any[ResourceServerId], any[PermissionId]) returns testExceptionWrappedE[PermissionNotFoundError]

        permissionRepository
          .delete(publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getBy(:publicResourceServerId, :publicPermissionId)" when {

    "should always call PermissionDb" in {
      permissionDb.getBy(any[ResourceServerId], any[PermissionId]) returns Option(permissionEntityRead_1)
        .pure[doobie.ConnectionIO]

      for {
        _ <- permissionRepository.getBy(publicResourceServerId_1, publicPermissionId_1)

        _ = verify(permissionDb).getBy(eqTo(publicResourceServerId_1), eqTo(publicPermissionId_1))
      } yield ()
    }

    "PermissionDb returns empty Option" should {
      "return empty Option" in {
        permissionDb.getBy(any[ResourceServerId], any[PermissionId]) returns Option
          .empty[PermissionEntity.Read]
          .pure[doobie.ConnectionIO]

        permissionRepository.getBy(publicResourceServerId_1, publicPermissionId_1).asserting(_ shouldBe None)
      }
    }

    "PermissionDb returns Option containing PermissionEntity" should {
      "return Option containing Permission" in {
        permissionDb.getBy(any[ResourceServerId], any[PermissionId]) returns Option(
          permissionEntityRead_1
        )
          .pure[doobie.ConnectionIO]

        permissionRepository
          .getBy(publicResourceServerId_1, publicPermissionId_1)
          .asserting(_ shouldBe Some(permission_1))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getBy(any[ResourceServerId], any[PermissionId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]]

        permissionRepository
          .getBy(publicResourceServerId_1, publicPermissionId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getAllPermissionsForTemplate" when {

    "should always call PermissionDb" in {
      permissionDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

      for {
        _ <- permissionRepository.getAllFor(publicTemplateId_1)

        _ = verify(permissionDb).getAllForTemplate(eqTo(publicTemplateId_1))
      } yield ()
    }

    "PermissionDb returns empty Stream" should {
      "return empty List" in {
        permissionDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

        permissionRepository
          .getAllFor(publicTemplateId_1)
          .asserting(_ shouldBe List.empty[Permission])
      }
    }

    "PermissionDb returns PermissionEntities in Stream" should {
      "return List containing Permissions" in {
        permissionDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )

        permissionRepository
          .getAllFor(publicTemplateId_1)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllForTemplate(any[ApiKeyTemplateId]) returns Stream.raiseError[doobie.ConnectionIO](
          testException
        )

        permissionRepository
          .getAllFor(publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "PermissionRepository on getAllBy" when {

    val nameFragment = Some("test:name:fragment")

    "should always call PermissionDb" in {
      permissionDb.getAllBy(any[ResourceServerId])(any[Option[String]]) returns Stream.empty

      for {
        _ <- permissionRepository.getAllBy(publicResourceServerId_1)(nameFragment)

        _ = verify(permissionDb).getAllBy(eqTo(publicResourceServerId_1))(eqTo(nameFragment))
      } yield ()
    }

    "PermissionDb returns empty Stream" should {
      "return empty List" in {
        permissionDb.getAllBy(any[ResourceServerId])(any[Option[String]]) returns Stream.empty

        permissionRepository
          .getAllBy(publicResourceServerId_1)(nameFragment)
          .asserting(_ shouldBe List.empty[Permission])
      }
    }

    "PermissionDb returns PermissionEntities in Stream" should {
      "return List containing Permissions" in {
        permissionDb.getAllBy(any[ResourceServerId])(any[Option[String]]) returns Stream(
          permissionEntityRead_1,
          permissionEntityRead_2,
          permissionEntityRead_3
        )

        permissionRepository
          .getAllBy(publicResourceServerId_1)(nameFragment)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllBy(any[ResourceServerId])(any[Option[String]]) returns Stream
          .raiseError[doobie.ConnectionIO](
            testException
          )

        permissionRepository
          .getAllBy(publicResourceServerId_1)(nameFragment)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
