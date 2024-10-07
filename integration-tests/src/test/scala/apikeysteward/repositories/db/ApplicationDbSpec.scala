package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.IntegrationTestData._
import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationAlreadyExistsError
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ApplicationEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApplicationDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, application CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val applicationDb = new ApplicationDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApplications: doobie.ConnectionIO[List[ApplicationEntity.Read]] =
      sql"SELECT * FROM application".query[ApplicationEntity.Read].stream.compile.toList
  }

  "ApplicationDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          res <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
        } yield res).transact(transactor)

        result.asserting(res =>
          res shouldBe Right(applicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId))
        )
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val resultApplication = allApplications.head
          resultApplication shouldBe applicationEntityRead_1.copy(
            id = resultApplication.id,
            tenantId = resultApplication.tenantId
          )
        }
      }
    }

    "there is a row in the DB with a different applicationId" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
        } yield res).transact(transactor)

        result.asserting(res =>
          res shouldBe Right(applicationEntityRead_2.copy(id = res.value.id, tenantId = res.value.tenantId))
        )
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          res <- Queries.getAllApplications
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allApplications, entityRead_1, entityRead_2) =>
          allApplications.size shouldBe 2

          val expectedApplications = Seq(
            applicationEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            applicationEntityRead_2.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allApplications should contain theSameElementsAs expectedApplications
        }
      }
    }

    "there is a row in the DB with the same applicationId" should {

      "return Left containing ApplicationAlreadyExistsError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb
            .insert(applicationEntityWrite_2.copy(tenantId = tenantId, publicApplicationId = publicApplicationIdStr_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(ApplicationAlreadyExistsError(publicApplicationIdStr_1))
          res.left.value.message shouldBe s"Application with publicApplicationId = $publicApplicationIdStr_1 already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).transact(transactor)

          _ <- applicationDb
            .insert(applicationEntityWrite_2.copy(tenantId = tenantId, publicApplicationId = publicApplicationIdStr_1))
            .transact(transactor)
          res <- Queries.getAllApplications.transact(transactor)
        } yield res

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val resultApplication = allApplications.head
          resultApplication shouldBe applicationEntityRead_1.copy(
            id = resultApplication.id,
            tenantId = resultApplication.tenantId
          )
        }
      }
    }
  }

}
