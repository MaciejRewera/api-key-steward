package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.PermissionsTestData.{
  permissionEntityRead_1,
  permissionEntityRead_2,
  permissionEntityRead_3
}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantEntityRead_1}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.{
  ApiKeyTemplateInsertionErrorImpl,
  ReferencedTenantDoesNotExistError
}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, PermissionEntity, TenantEntity}
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesPermissionsDb, PermissionDb, TenantDb}
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

  private val tenantDb = mock[TenantDb]
  private val apiKeyTemplateDb = mock[ApiKeyTemplateDb]
  private val permissionDb = mock[PermissionDb]
  private val apiKeyTemplatesPermissionsDb = mock[ApiKeyTemplatesPermissionsDb]

  private val apiKeyTemplateRepository =
    new ApiKeyTemplateRepository(tenantDb, apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(tenantDb, apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)

  private val apiKeyTemplateNotFoundError = ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)
  private val apiKeyTemplateNotFoundErrorWrapped =
    apiKeyTemplateNotFoundError.asLeft[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")

  private def testExceptionWrappedE[E]: doobie.ConnectionIO[Either[E, ApiKeyTemplateEntity.Read]] =
    testException.raiseError[doobie.ConnectionIO, Either[E, ApiKeyTemplateEntity.Read]]

  "ApiKeyTemplateRepository on insert" when {

    val tenantId = 13L
    val tenantEntityReadWrapped = Option(tenantEntityRead_1.copy(id = tenantId)).pure[doobie.ConnectionIO]

    val apiKeyTemplateEntityReadWrapped =
      apiKeyTemplateEntityRead_1.asRight[ApiKeyTemplateInsertionError].pure[doobie.ConnectionIO]

    val testSqlException = new SQLException("Test SQL Exception")

    val apiKeyTemplateInsertionError: ApiKeyTemplateInsertionError = ApiKeyTemplateInsertionErrorImpl(testSqlException)
    val apiKeyTemplateInsertionErrorWrapped =
      apiKeyTemplateInsertionError.asLeft[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call TenantDb, ApiKeyTemplateDb and PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1)

          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
          expectedApiKeyTemplateEntityWrite = apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId)
          _ = verify(apiKeyTemplateDb).insert(eqTo(expectedApiKeyTemplateEntityWrite))
          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(apiKeyTemplate_1.publicTemplateId))
        } yield ()
      }

      "return Right containing ApiKeyTemplate" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream.empty

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Right(apiKeyTemplate_1.copy(permissions = List.empty)))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call ApiKeyTemplateDb or PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1)

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call ApiKeyTemplateDb or PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
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
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateInsertionErrorWrapped

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateInsertionErrorWrapped

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Left(apiKeyTemplateInsertionError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {

      "NOT call PermissionDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb
          .insert(any[ApiKeyTemplateEntity.Write]) returns testExceptionWrappedE[ApiKeyTemplateInsertionError]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
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
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream
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
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
          .covary[ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.update(apiKeyTemplateUpdate)

          _ = verify(apiKeyTemplateDb).update(eqTo(apiKeyTemplateEntityUpdate_1))
          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Right containing updated ApiKeyTemplate" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)
          .covary[ConnectionIO]

        val expectedUpdatedApiKeyTemplate = apiKeyTemplate_1.copy(
          name = apiKeyTemplateNameUpdated,
          description = apiKeyTemplateDescriptionUpdated,
          isDefault = true,
          apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
        )

        apiKeyTemplateRepository
          .update(apiKeyTemplateUpdate)
          .asserting(_ shouldBe Right(expectedUpdatedApiKeyTemplate))
      }
    }

    "ApiKeyTemplateDb returns Left containing ApiKeyTemplateNotFoundError" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns apiKeyTemplateNotFoundErrorWrapped

        for {
          _ <- apiKeyTemplateRepository.update(apiKeyTemplateUpdate)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns apiKeyTemplateNotFoundErrorWrapped

        apiKeyTemplateRepository.update(apiKeyTemplateUpdate).asserting(_ shouldBe Left(apiKeyTemplateNotFoundError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb
          .update(any[ApiKeyTemplateEntity.Update]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        for {
          _ <- apiKeyTemplateRepository.update(apiKeyTemplateUpdate).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb
          .update(any[ApiKeyTemplateEntity.Update]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        apiKeyTemplateRepository.update(apiKeyTemplateUpdate).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream.raiseError[ConnectionIO](
          testException
        )

        apiKeyTemplateRepository.update(apiKeyTemplateUpdate).attempt.asserting(_ shouldBe Left(testException))
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

      "call ApiKeyTemplatesPermissionsDb, ApiKeyTemplateDb and PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        for {
          _ <- apiKeyTemplateRepository.delete(publicTemplateId_1)

          _ = verify(apiKeyTemplatesPermissionsDb).deleteAllForApiKeyTemplate(eqTo(publicTemplateId_1))
          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
          _ = verify(apiKeyTemplateDb).delete(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Right containing deleted ApiKeyTemplate" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        apiKeyTemplateRepository.delete(publicTemplateId_1).asserting(_ shouldBe Right(apiKeyTemplate_1))
      }
    }

    "ApiKeyTemplatesPermissionsDb returns exception" should {

      "NOT call either ApiKeyTemplateDb or PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- apiKeyTemplateRepository.delete(publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Int]

        apiKeyTemplateRepository.delete(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateDb returns Left containing ApiKeyTemplateNotFoundError" should {

      "NOT call PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns apiKeyTemplateNotFoundWrapped

        for {
          _ <- apiKeyTemplateRepository.delete(publicTemplateId_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing this error" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns apiKeyTemplateNotFoundWrapped

        apiKeyTemplateRepository.delete(publicTemplateId_1).asserting(_ shouldBe Left(apiKeyTemplateNotFoundError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        for {
          _ <- apiKeyTemplateRepository.delete(publicTemplateId_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        apiKeyTemplateRepository.delete(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb return exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(any[ApiKeyTemplateId]) returns 3
          .pure[doobie.ConnectionIO]
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns
          Stream(permissionEntityRead_1) ++ Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyTemplateRepository.delete(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getBy(:apiKeyTemplateId)" when {

    "should always call ApiKeyTemplateDb and PermissionDb" in {
      apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
        .pure[doobie.ConnectionIO]
      permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

      for {
        _ <- apiKeyTemplateRepository.getBy(publicTemplateId_1)

        _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTemplateId_1))
        _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
      } yield ()
    }

    "ApiKeyTemplateDb returns empty Option" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.getBy(publicTemplateId_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return empty Option" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplateRepository.getBy(publicTemplateId_1).asserting(_ shouldBe None)
      }
    }

    "ApiKeyTemplateDb returns Option containing ApiKeyTemplateEntity" should {

      "call PermissionDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        for {
          _ <- apiKeyTemplateRepository.getBy(publicTemplateId_1)

          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Option containing ApiKeyTemplate" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        apiKeyTemplateRepository.getBy(publicTemplateId_1).asserting(_ shouldBe Some(apiKeyTemplate_1))
      }
    }

    "ApiKeyTemplateDb returns exception" should {

      "NOT call PermissionDb" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyTemplateRepository.getBy(publicTemplateId_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplateRepository.getBy(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(
          permissionEntityRead_1
        ) ++ Stream.raiseError[ConnectionIO](testException)

        apiKeyTemplateRepository.getBy(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getAllForTenant" when {

    "should always call ApiKeyTemplateDb and PermissionDb" in {
      apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(apiKeyTemplateEntityRead_1)
      permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

      for {
        _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1)

        _ = verify(apiKeyTemplateDb).getAllForTenant(eqTo(publicTenantId_1))
        _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
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
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2),
          Stream(permissionEntityRead_3)
        )

        for {
          _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1)

          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_2))
          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_3))
        } yield ()
      }

      "return List containing ApiKeyTemplates" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns (
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
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

        for {
          _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).attempt

          _ = verify(permissionDb).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
          _ = verifyNoMoreInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(apiKeyTemplateEntityRead_1) ++ Stream
          .raiseError[doobie.ConnectionIO](testException)
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns Stream(permissionEntityRead_1)

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
        permissionDb.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns (
          Stream(permissionEntityRead_1),
          Stream.raiseError[doobie.ConnectionIO](testException)
        )

        apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
