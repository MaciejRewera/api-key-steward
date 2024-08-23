package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.IntegrationTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.DbCommons.ApiKeyTemplateInsertionError.ApiKeyTemplateAlreadyExistsError
import apikeysteward.repositories.db.DbCommons.ApiKeyTemplateUpdateError.ApiKeyTemplateNotFoundError
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import fs2.Stream
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class ApiKeyTemplateDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with FixedClock
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE api_key_template CASCADE".update.run
  } yield ()

  private val apiKeyTemplateDb = new ApiKeyTemplateDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllTemplates: Stream[doobie.ConnectionIO, ApiKeyTemplateEntity.Read] =
      sql"SELECT * FROM api_key_template".query[ApiKeyTemplateEntity.Read].stream
  }

  private implicit class ApiKeyTemplateEntityReadToWrite(apiKeyTemplateEntityRead: ApiKeyTemplateEntity.Read) {
    def toWrite: ApiKeyTemplateEntity.Write =
      ApiKeyTemplateEntity.Write(
        publicId = apiKeyTemplateEntityRead.publicId,
        apiKeyExpiryPeriodMaxSeconds = apiKeyTemplateEntityRead.apiKeyExpiryPeriodMaxSeconds
      )
  }

  "ApiKeyTemplateDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor).asserting { res =>
          res.value shouldBe apiKeyTemplateEntityRead_1.copy(id = res.value.id)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- Queries.getAllTemplates.compile.toList
        } yield res).transact(transactor)

        result.asserting { resTemplates =>
          resTemplates shouldBe List(apiKeyTemplateEntityRead_1.copy(id = resTemplates.head.id))
        }
      }
    }

    "there is a row in the DB with the same publicKey" should {

      "return Left containing ApiKeyTemplateAlreadyExistsError" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res.left.value shouldBe an[ApiKeyTemplateAlreadyExistsError]
          res.left.value.message shouldBe s"API Key Template with publicId: [${apiKeyTemplateEntityWrite_1.publicId}] already exists."
        }
      }

      "NOT insert the new entity into DB" in {
        val result = for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor)

          res <- Queries.getAllTemplates.transact(transactor).compile.toList
        } yield res

        result.asserting { resTemplates =>
          resTemplates shouldBe List(apiKeyTemplateEntityRead_1.copy(id = resTemplates.head.id))
        }
      }
    }

    "there is a row in the DB with a different publicId" should {

      "return inserted entity" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)

          res <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_.value.toWrite shouldBe apiKeyTemplateEntityWrite_1)
      }

      "insert the new entity into DB" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- Queries.getAllTemplates.compile.toList
        } yield res).transact(transactor)

        result.asserting { resTemplates =>
          resTemplates.map(_.toWrite) should contain theSameElementsAs List(
            apiKeyTemplateEntityWrite_1,
            apiKeyTemplateEntityWrite_2
          )
          resTemplates.map(_.createdAt) shouldBe List(nowInstant, nowInstant)
          resTemplates.map(_.updatedAt) shouldBe List(nowInstant, nowInstant)
        }
      }
    }
  }

  "ApiKeyTemplateDb on update" when {

    "there are no rows in the DB" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        apiKeyTemplateDb.update(apiKeyTemplateEntityWrite_1).transact(transactor).asserting { res =>
          res.left.value shouldBe an[ApiKeyTemplateNotFoundError]
          res.left.value.message shouldBe s"API Key Template with publicId: [${apiKeyTemplateEntityWrite_1.publicId}] does not exist."
        }
      }

      "make no changes in the DB" in {
        val result = (for {
          templatesBefore <- Queries.getAllTemplates.compile.toList

          _ <- apiKeyTemplateDb.update(apiKeyTemplateEntityWrite_1)

          templatesAfter <- Queries.getAllTemplates.compile.toList
        } yield (templatesBefore, templatesAfter)).transact(transactor)

        result.asserting { case (templatesBefore, templatesAfter) =>
          templatesBefore shouldBe List.empty
          templatesAfter shouldBe List.empty
        }
      }
    }

    "there are rows in the DB with different publicIds" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val publicId = UUID.randomUUID().toString
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)

          res <- apiKeyTemplateDb.update(apiKeyTemplateEntityWrite_1.copy(publicId = publicId))
        } yield res).transact(transactor)

        result.asserting { res =>
          res.left.value shouldBe an[ApiKeyTemplateNotFoundError]
          res.left.value.message shouldBe s"API Key Template with publicId: [${publicId}] does not exist."
        }
      }

      "make no changes in the DB" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)
          templatesBefore <- Queries.getAllTemplates.compile.toList

          _ <- apiKeyTemplateDb.update(apiKeyTemplateEntityWrite_1)

          templatesAfter <- Queries.getAllTemplates.compile.toList
        } yield (templatesBefore, templatesAfter)).transact(transactor)

        result.asserting { case (templatesBefore, templatesAfter) =>
          templatesBefore should contain theSameElementsAs templatesAfter
        }
      }
    }

    "there is a row in the DB with the same publicId" should {

      "return updated row" in {
        val newApiKeyExpiryPeriodMaxSeconds = 42
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)

          res <- apiKeyTemplateDb.update(
            apiKeyTemplateEntityWrite_1.copy(apiKeyExpiryPeriodMaxSeconds = newApiKeyExpiryPeriodMaxSeconds)
          )
        } yield res).transact(transactor)

        result.asserting { res =>
          res.value shouldBe apiKeyTemplateEntityRead_1.copy(
            id = res.value.id,
            apiKeyExpiryPeriodMaxSeconds = newApiKeyExpiryPeriodMaxSeconds
          )
        }
      }

      "update the row with the same publicId" in {
        val newApiKeyExpiryPeriodMaxSeconds = 42
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)
          templatesBefore <- Queries.getAllTemplates.compile.toList

          _ <- apiKeyTemplateDb.update(
            apiKeyTemplateEntityWrite_1.copy(apiKeyExpiryPeriodMaxSeconds = newApiKeyExpiryPeriodMaxSeconds)
          )

          templatesAfter <- Queries.getAllTemplates.compile.toList
        } yield (templatesBefore, templatesAfter)).transact(transactor)

        result.asserting { case (templatesBefore, templatesAfter) =>

          templatesBefore.map(_.toWrite) should contain theSameElementsAs List(
            apiKeyTemplateEntityWrite_1,
            apiKeyTemplateEntityWrite_2
          )
          templatesAfter.map(_.toWrite) should contain theSameElementsAs List(
            apiKeyTemplateEntityWrite_1.copy(apiKeyExpiryPeriodMaxSeconds = newApiKeyExpiryPeriodMaxSeconds),
            apiKeyTemplateEntityWrite_2
          )
        }
      }
    }
  }

  "ApiKeyTemplateDb on getBy(:publicId)" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        apiKeyTemplateDb.getBy(UUID.randomUUID()).transact(transactor).asserting(_ shouldBe None)
      }
    }

    "there are rows in the DB with different public ID" should {
      "return empty Option" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)

          res <- apiKeyTemplateDb.getBy(UUID.randomUUID())
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same public ID" should {
      "return this row" in {
        val result = (for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2)
          publicId = UUID.fromString(apiKeyTemplateEntityWrite_1.publicId)

          res <- apiKeyTemplateDb.getBy(publicId)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Some(apiKeyTemplateEntityRead_1.copy(id = res.get.id)))
      }
    }
  }

}
