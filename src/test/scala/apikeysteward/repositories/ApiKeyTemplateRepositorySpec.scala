package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{publicTemplateId_1, _}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantEntityRead_1}
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, TenantEntity}
import apikeysteward.repositories.db.{
  ApiKeyTemplateDb,
  ApiKeyTemplatesPermissionsDb,
  ApiKeyTemplatesUsersDb,
  PermissionDb,
  TenantDb
}
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import doobie.ConnectionIO
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyNoMoreInteractions, verifyZeroInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.sql.SQLException

class ApiKeyTemplateRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val uuidGenerator = mock[UuidGenerator]
  private val tenantDb = mock[TenantDb]
  private val apiKeyTemplateDb = mock[ApiKeyTemplateDb]
  private val permissionDb = mock[PermissionDb]
  private val apiKeyTemplatesPermissionsDb = mock[ApiKeyTemplatesPermissionsDb]
  private val apiKeyTemplatesUsersDb = mock[ApiKeyTemplatesUsersDb]

  private val apiKeyTemplateRepository =
    new ApiKeyTemplateRepository(
      uuidGenerator,
      tenantDb,
      apiKeyTemplateDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeyTemplatesUsersDb
    )(noopTransactor)

  override def beforeEach(): Unit =
    reset(uuidGenerator, tenantDb, apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb, apiKeyTemplatesUsersDb)

  private val apiKeyTemplateNotFoundError = ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)
  private val apiKeyTemplateNotFoundErrorWrapped =
    apiKeyTemplateNotFoundError.asLeft[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, ApiKeyTemplateEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, ApiKeyTemplateEntity.Read]]

  "ApiKeyTemplateRepository on insert" when {

    val tenantEntityReadWrapped = Option(tenantEntityRead_1).pure[doobie.ConnectionIO]

    val apiKeyTemplateEntityReadWrapped =
      apiKeyTemplateEntityRead_1.asRight[ApiKeyTemplateInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")

    val apiKeyTemplateInsertionError: ApiKeyTemplateInsertionError = ApiKeyTemplateInsertionErrorImpl(testSqlException)
    val apiKeyTemplateInsertionErrorWrapped =
      apiKeyTemplateInsertionError.asLeft[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call UuidGenerator, TenantDb, ApiKeyTemplateDb and PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.empty

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1)

          _ = verify(uuidGenerator).generateUuid
          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
          _ = verify(apiKeyTemplateDb).insert(eqTo(apiKeyTemplateEntityWrite_1))
          _ = verify(permissionDb).getAllForTemplate(eqTo(publicTenantId_1), eqTo(apiKeyTemplate_1.publicTemplateId))
        } yield ()
      }

      "return Right containing ApiKeyTemplate" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.empty

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Right(apiKeyTemplate_1.copy(permissions = List.empty)))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantDb, ApiKeyTemplateDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(tenantDb, apiKeyTemplateDb, permissionDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call ApiKeyTemplateDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1)

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call ApiKeyTemplateDb or PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateDb returns Left containing ApiKeyTemplateInsertionError" should {

      "NOT call PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateInsertionErrorWrapped

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateInsertionErrorWrapped

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Left(apiKeyTemplateInsertionError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {

      "NOT call PermissionDb" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb
          .insert(any[ApiKeyTemplateEntity.Write]) returns testExceptionWrappedE[ApiKeyTemplateInsertionError]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb
          .insert(any[ApiKeyTemplateEntity.Write]) returns testExceptionWrappedE[ApiKeyTemplateInsertionError]

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(templateDbId_1)
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream
          .raiseError[doobie.ConnectionIO](testException)

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on update" when {

    val updatedApiKeyTemplateEntityReadWrapped =
      apiKeyTemplateEntityRead_1
        .copy(
          isDefault = true,
          name = apiKeyTemplateNameUpdated,
          description = apiKeyTemplateDescriptionUpdated,
          apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
        )
        .asRight[ApiKeyTemplateNotFoundError]
        .pure[doobie.ConnectionIO]

    val apiKeyTemplateUpdate: ApiKeyTemplateUpdate = ApiKeyTemplateUpdate(
      publicTemplateId = publicTemplateId_1,
      name = apiKeyTemplateNameUpdated,
      description = apiKeyTemplateDescriptionUpdated,
      isDefault = true,
      apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
    )

    "everything works correctly" should {

      "call ApiKeyTemplateDb and PermissionDb" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
          .covary[ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.update(publicTenantId_1, apiKeyTemplateUpdate)

          _ = verify(apiKeyTemplateDb).update(eqTo(apiKeyTemplateEntityUpdate_1))
          _ = verify(permissionDb).getAllForTemplate(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Right containing updated ApiKeyTemplate" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
          .covary[ConnectionIO]

        val expectedUpdatedApiKeyTemplate = apiKeyTemplate_1.copy(
          name = apiKeyTemplateNameUpdated,
          description = apiKeyTemplateDescriptionUpdated,
          isDefault = true,
          apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
        )

        apiKeyTemplateRepository
          .update(publicTenantId_1, apiKeyTemplateUpdate)
          .asserting(_ shouldBe Right(expectedUpdatedApiKeyTemplate))
      }
    }

    "ApiKeyTemplateDb returns Left containing ApiKeyTemplateNotFoundError" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns apiKeyTemplateNotFoundErrorWrapped

        for {
          _ <- apiKeyTemplateRepository.update(publicTenantId_1, apiKeyTemplateUpdate)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns apiKeyTemplateNotFoundErrorWrapped

        apiKeyTemplateRepository
          .update(publicTenantId_1, apiKeyTemplateUpdate)
          .asserting(_ shouldBe Left(apiKeyTemplateNotFoundError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb
          .update(any[ApiKeyTemplateEntity.Update]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        for {
          _ <- apiKeyTemplateRepository.update(publicTenantId_1, apiKeyTemplateUpdate).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb
          .update(any[ApiKeyTemplateEntity.Update]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        apiKeyTemplateRepository
          .update(publicTenantId_1, apiKeyTemplateUpdate)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.raiseError[ConnectionIO](
          testException
        )

        apiKeyTemplateRepository
          .update(publicTenantId_1, apiKeyTemplateUpdate)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on delete" when {

    val deletedApiKeyTemplateEntityReadWrapped =
      apiKeyTemplateEntityRead_1.asRight[ApiKeyTemplateNotFoundError].pure[doobie.ConnectionIO]

    val apiKeyTemplateNotFound = apiKeyTemplateNotFoundError
    val apiKeyTemplateNotFoundWrapped =
      apiKeyTemplateNotFound.asLeft[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsDb, ApiKeyTemplatesUsersDb, ApiKeyTemplateDb and PermissionDb" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3.pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped

        for {
          _ <- apiKeyTemplateRepository.delete(publicTenantId_1, publicTemplateId_1)

          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1)
          )
          _ = verify(apiKeyTemplatesPermissionsDb).deleteAllForApiKeyTemplate(eqTo(publicTemplateId_1))
          _ = verify(apiKeyTemplatesUsersDb).deleteAllForApiKeyTemplate(eqTo(publicTemplateId_1))
          _ = verify(apiKeyTemplateDb).delete(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Right containing deleted ApiKeyTemplate" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3.pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped

        apiKeyTemplateRepository
          .delete(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Right(apiKeyTemplate_1))
      }
    }

    "PermissionDb return exception" should {

      "Not call ApiKeyTemplatesPermissionsDb, ApiKeyTemplatesUsersDb or ApiKeyTemplateDb" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns
          Stream(permissionEntityRead_1) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- apiKeyTemplateRepository.delete(publicTenantId_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplatesPermissionsDb, apiKeyTemplatesUsersDb, apiKeyTemplateDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns
          Stream(permissionEntityRead_1) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyTemplateRepository
          .delete(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {

      "NOT call either ApiKeyTemplatesUsersDb or ApiKeyTemplateDb" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- apiKeyTemplateRepository.delete(publicTenantId_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplatesUsersDb, apiKeyTemplateDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        apiKeyTemplateRepository
          .delete(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplatesUsersDb returns exception" should {

      "NOT call ApiKeyTemplateDb" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- apiKeyTemplateRepository.delete(publicTenantId_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        apiKeyTemplateRepository
          .delete(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateDb returns Left containing ApiKeyTemplateNotFoundError" should {
      "return Left containing this error" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3.pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns apiKeyTemplateNotFoundWrapped

        apiKeyTemplateRepository
          .delete(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(apiKeyTemplateNotFoundError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3.pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        apiKeyTemplateRepository
          .delete(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getBy(:apiKeyTemplateId)" when {

    "should always call ApiKeyTemplateDb and PermissionDb" in {
      apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
        .pure[doobie.ConnectionIO]
      permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

      for {
        _ <- apiKeyTemplateRepository.getBy(publicTenantId_1, publicTemplateId_1)

        _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTemplateId_1))
        _ = verify(permissionDb).getAllForTemplate(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
      } yield ()
    }

    "ApiKeyTemplateDb returns empty Option" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.getBy(publicTenantId_1, publicTemplateId_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty Option" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplateRepository.getBy(publicTenantId_1, publicTemplateId_1).asserting(_ shouldBe None)
      }
    }

    "ApiKeyTemplateDb returns Option containing ApiKeyTemplateEntity" should {

      "call PermissionDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        for {
          _ <- apiKeyTemplateRepository.getBy(publicTenantId_1, publicTemplateId_1)

          _ = verify(permissionDb).getAllForTemplate(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Option containing ApiKeyTemplate" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        apiKeyTemplateRepository
          .getBy(publicTenantId_1, publicTemplateId_1)
          .asserting(_ shouldBe Some(apiKeyTemplate_1))
      }
    }

    "ApiKeyTemplateDb returns exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyTemplateRepository.getBy(publicTenantId_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplateRepository
          .getBy(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream.raiseError[ConnectionIO](testException)

        apiKeyTemplateRepository
          .getBy(publicTenantId_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getAllForTenant" when {

    "should always call ApiKeyTemplateDb" in {
      apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(apiKeyTemplateEntityRead_1)
      permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.empty

      for {
        _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1)

        _ = verify(apiKeyTemplateDb).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "ApiKeyTemplateDb returns empty Stream" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream.empty

        for {
          _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty List" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream.empty

        apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[ApiKeyTemplate])
      }
    }

    "ApiKeyTemplateDb returns ApiKeyTemplateEntities in Stream" should {

      "call PermissionDb for each ApiKeyTemplateEntity" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        for {
          _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1)

          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1)
          )
          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_2)
          )
          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_3)
          )
        } yield ()
      }

      "return List containing ApiKeyTemplates" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        apiKeyTemplateRepository
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3))
      }
    }

    "ApiKeyTemplateDb returns exception in the middle of the Stream" should {

      "only call PermissionDb for elements before the exception" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(apiKeyTemplateEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        for {
          _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).attempt

          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1)
          )
          _ = verifyNoMoreInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(apiKeyTemplateEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception for one of subsequent calls" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream.raiseError[doobie.ConnectionIO](testException)
        )

        apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getAllForUser" when {

    "should always call ApiKeyTemplateDb" in {
      apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream(apiKeyTemplateEntityRead_1)
      permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream.empty

      for {
        _ <- apiKeyTemplateRepository.getAllForUser(publicTenantId_1, publicUserId_1)

        _ = verify(apiKeyTemplateDb).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
      } yield ()
    }

    "ApiKeyTemplateDb returns empty Stream" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream.empty

        for {
          _ <- apiKeyTemplateRepository.getAllForUser(publicTenantId_1, publicUserId_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty List" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream.empty

        apiKeyTemplateRepository.getAllForUser(publicTenantId_1, publicUserId_1).asserting(_ shouldBe List.empty)
      }
    }

    "ApiKeyTemplateDb returns ApiKeyTemplateEntities in Stream" should {

      "call PermissionDb for each ApiKeyTemplateEntity" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        for {
          _ <- apiKeyTemplateRepository.getAllForUser(publicTenantId_1, publicUserId_1)

          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1)
          )
          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_2)
          )
          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_3)
          )
        } yield ()
      }

      "return List containing ApiKeyTemplates" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        apiKeyTemplateRepository
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .asserting(_ should contain theSameElementsAs List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3))
      }
    }

    "ApiKeyTemplateDb returns exception in the middle of the Stream" should {

      "only call PermissionDb for elements before the exception" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream(apiKeyTemplateEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        for {
          _ <- apiKeyTemplateRepository.getAllForUser(publicTenantId_1, publicUserId_1).attempt

          _ = verify(permissionDb).getAllForTemplate(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1)
          )
          _ = verifyNoMoreInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream(apiKeyTemplateEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        apiKeyTemplateRepository
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception for one of subsequent calls" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getAllForUser(any[TenantId], any[UserId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllForTemplate(any[TenantId], any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream.raiseError[doobie.ConnectionIO](testException)
        )

        apiKeyTemplateRepository
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
