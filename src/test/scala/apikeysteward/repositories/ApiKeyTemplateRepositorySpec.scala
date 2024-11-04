package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantEntityRead_1}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError.{
  ApiKeyTemplateInsertionErrorImpl,
  ReferencedTenantDoesNotExistError
}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, TenantEntity}
import apikeysteward.repositories.db.{ApiKeyTemplateDb, TenantDb}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyZeroInteractions}
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

  private val apiKeyTemplateRepository = new ApiKeyTemplateRepository(tenantDb, apiKeyTemplateDb)(noopTransactor)

  override def beforeEach(): Unit =
    reset(tenantDb, apiKeyTemplateDb)

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

      "call TenantDb and ApiKeyTemplateDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1)

          expectedApiKeyTemplateEntityWrite = apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId)
          _ = verify(apiKeyTemplateDb).insert(eqTo(expectedApiKeyTemplateEntityWrite))
        } yield ()
      }

      "return Right containing ApiKeyTemplate" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateEntityReadWrapped

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Right(apiKeyTemplate_1))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call ApiKeyTemplateDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1)

          _ = verifyZeroInteractions(apiKeyTemplateDb)
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

      "NOT call ApiKeyTemplateDb" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- apiKeyTemplateRepository.insert(publicTenantId_1, apiKeyTemplate_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb)
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
      "return Left containing this error" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyTemplateDb.insert(any[ApiKeyTemplateEntity.Write]) returns apiKeyTemplateInsertionErrorWrapped

        apiKeyTemplateRepository
          .insert(publicTenantId_1, apiKeyTemplate_1)
          .asserting(_ shouldBe Left(apiKeyTemplateInsertionError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {
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

      "call ApiKeyTemplateDb" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped

        for {
          _ <- apiKeyTemplateRepository.update(apiKeyTemplateUpdate)

          _ = verify(apiKeyTemplateDb).update(eqTo(apiKeyTemplateEntityUpdate_1))
        } yield ()
      }

      "return Right containing updated ApiKeyTemplate" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns updatedApiKeyTemplateEntityReadWrapped

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
      "return Left containing this error" in {
        apiKeyTemplateDb.update(any[ApiKeyTemplateEntity.Update]) returns apiKeyTemplateNotFoundErrorWrapped

        apiKeyTemplateRepository.update(apiKeyTemplateUpdate).asserting(_ shouldBe Left(apiKeyTemplateNotFoundError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb
          .update(any[ApiKeyTemplateEntity.Update]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

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

      "call ApiKeyTemplateDb" in {
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped

        for {
          _ <- apiKeyTemplateRepository.delete(publicTemplateId_1)

          _ = verify(apiKeyTemplateDb).delete(eqTo(publicTemplateId_1))
        } yield ()
      }

      "return Right containing deleted ApiKeyTemplate" in {
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns deletedApiKeyTemplateEntityReadWrapped

        apiKeyTemplateRepository.delete(publicTemplateId_1).asserting(_ shouldBe Right(apiKeyTemplate_1))
      }
    }

    "ApiKeyTemplateDb returns Left containing ApiKeyTemplateNotFoundError" should {
      "return Left containing this error" in {
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns apiKeyTemplateNotFoundWrapped

        apiKeyTemplateRepository.delete(publicTemplateId_1).asserting(_ shouldBe Left(apiKeyTemplateNotFoundError))
      }
    }

    "ApiKeyTemplateDb returns different exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.delete(any[ApiKeyTemplateId]) returns testExceptionWrappedE[ApiKeyTemplateNotFoundError]

        apiKeyTemplateRepository.delete(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getBy(:apiKeyTemplateId)" when {

    "should always call ApiKeyTemplateDb" in {
      apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
        .pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyTemplateRepository.getBy(publicTemplateId_1)

        _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTemplateId_1))
      } yield ()
    }

    "ApiKeyTemplateDb returns empty Option" should {
      "return empty Option" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns none[ApiKeyTemplateEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyTemplateRepository.getBy(publicTemplateId_1).asserting(_ shouldBe None)
      }
    }

    "ApiKeyTemplateDb returns Option containing ApiKeyTemplateEntity" should {
      "return Option containing ApiKeyTemplate" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns Option(apiKeyTemplateEntityRead_1)
          .pure[doobie.ConnectionIO]

        apiKeyTemplateRepository.getBy(publicTemplateId_1).asserting(_ shouldBe Some(apiKeyTemplate_1))
      }
    }

    "ApiKeyTemplateDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getByPublicTemplateId(any[ApiKeyTemplateId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyTemplateRepository.getBy(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateRepository on getAllForTenant" when {

    "should always call ApiKeyTemplateDb" in {
      apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream.empty

      for {
        _ <- apiKeyTemplateRepository.getAllForTenant(publicTenantId_1)

        _ = verify(apiKeyTemplateDb).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "ApiKeyTemplateDb returns empty Stream" should {
      "return empty List" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream.empty

        apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[ApiKeyTemplate])
      }
    }

    "ApiKeyTemplateDb returns ApiKeyTemplateEntities in Stream" should {
      "return List containing ApiKeyTemplates" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream(
          apiKeyTemplateEntityRead_1,
          apiKeyTemplateEntityRead_2,
          apiKeyTemplateEntityRead_3
        )

        apiKeyTemplateRepository
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3))
      }
    }

    "ApiKeyTemplateDb returns exception" should {
      "return failed IO containing this exception" in {
        apiKeyTemplateDb.getAllForTenant(any[TenantId]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyTemplateRepository.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
