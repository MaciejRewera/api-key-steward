package apikeysteward.repositories.db

import apikeysteward.base.IntegrationTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.DbCommons.ScopeTemplateInsertionError.ScopeTemplateAlreadyExistsError
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ScopeTemplateEntity}
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import fs2.Stream
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ScopeTemplateDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE scope_template, api_key_template CASCADE".update.run
  } yield ()

  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val scopeTemplateDb = new ScopeTemplateDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllScopes: Stream[doobie.ConnectionIO, ScopeTemplateEntity.Read] =
      sql"SELECT * FROM scope_template".query[ScopeTemplateEntity.Read].stream
  }

  private def insertPrerequisiteData(apiKeyTemplateEntityWrite: ApiKeyTemplateEntity.Write): doobie.ConnectionIO[Long] =
    apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite).map(_.value.id)

  private implicit class ScopeTemplateEntityReadToWrite(scopeTemplateEntityRead: ScopeTemplateEntity.Read) {
    def toWrite: ScopeTemplateEntity.Write =
      ScopeTemplateEntity.Write(
        apiKeyTemplateId = scopeTemplateEntityRead.apiKeyTemplateId,
        value = scopeTemplateEntityRead.value,
        name = scopeTemplateEntityRead.name,
        description = scopeTemplateEntityRead.description
      )
  }

  private val scopeTemplateRead_1 = ScopeTemplateEntity.Write(
    apiKeyTemplateId = 123L,
    value = scopeRead_1,
    name = scopeReadName_1,
    description = Some(scopeReadDescription_1)
  )
  private val scopeTemplateRead_2 = ScopeTemplateEntity.Write(
    apiKeyTemplateId = 123L,
    value = scopeRead_2,
    name = scopeReadName_2,
    description = Some(scopeReadDescription_2)
  )
  private val scopeTemplateWrite_1 = ScopeTemplateEntity.Write(
    apiKeyTemplateId = 123L,
    value = scopeWrite_1,
    name = scopeWriteName_1,
    description = Some(scopeWriteDescription_1)
  )
  private val scopeTemplateWrite_2 = ScopeTemplateEntity.Write(
    apiKeyTemplateId = 123L,
    value = scopeWrite_2,
    name = scopeWriteName_2,
    description = Some(scopeWriteDescription_2)
  )
  private val scopeTemplateRead_3 = ScopeTemplateEntity.Write(
    apiKeyTemplateId = 123L,
    value = scopeRead_3,
    name = scopeReadName_3,
    description = Some(scopeReadDescription_3)
  )
  private val scopeTemplateWrite_3 = ScopeTemplateEntity.Write(
    apiKeyTemplateId = 123L,
    value = scopeWrite_3,
    name = scopeWriteName_3,
    description = Some(scopeWriteDescription_3)
  )

  "ScopeTemplateDb on insertMany" when {

    "provided with NO scope templates" should {

      val inputScopeTemplates = List.empty[ScopeTemplateEntity.Write]

      "return empty Stream" in {
        scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor).asserting(_.value shouldBe List.empty)
      }

      "NOT insert anything into DB" in {
        val result = (for {
          _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

          res <- Queries.getAllScopes.compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "provided with a single scope" when {

      "there are no rows in the DB" should {

        "return inserted entity" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            inputScopeTemplates = List(scopeTemplateRead_1.copy(apiKeyTemplateId = templateId))

            res <- scopeTemplateDb.insertMany(inputScopeTemplates)
          } yield (inputScopeTemplates, res)).transact(transactor)

          result.asserting { case (inputScopeTemplates, res) =>
            res.value.map(_.toWrite) shouldBe inputScopeTemplates
          }
        }

        "insert entity into DB" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            inputScopeTemplates = List(scopeTemplateRead_1.copy(apiKeyTemplateId = templateId))

            _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

            res <- Queries.getAllScopes.compile.toList
          } yield (inputScopeTemplates, res)).transact(transactor)

          result.asserting { case (inputScopeTemplates, res) =>
            res.map(_.toWrite) shouldBe inputScopeTemplates
          }
        }
      }

      "there is a row with the same apiKeyTemplateId and value in the DB" should {

        "return Left containing ScopeTemplateAlreadyExistsError" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            oldScopeTemplates = List(scopeTemplateRead_2.copy(apiKeyTemplateId = templateId, value = scopeRead_1))
            inputScopeTemplates = List(scopeTemplateRead_1.copy(apiKeyTemplateId = templateId))

            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

            res <- scopeTemplateDb.insertMany(inputScopeTemplates)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe Left(ScopeTemplateAlreadyExistsError))
        }

        "NOT insert the new entity into DB" in {
          val result = for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1).transact(transactor)
            oldScopeTemplates = List(scopeTemplateRead_2.copy(apiKeyTemplateId = templateId, value = scopeRead_1))
            inputScopeTemplates = List(scopeTemplateRead_1.copy(apiKeyTemplateId = templateId))

            _ <- scopeTemplateDb.insertMany(oldScopeTemplates).transact(transactor)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor)

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield (oldScopeTemplates, res)

          result.asserting { case (oldScopeTemplates, res) =>
            res.map(_.toWrite) shouldBe oldScopeTemplates
          }
        }
      }

      "there is a different row in the DB" should {

        "return inserted entity" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            oldScopeTemplates = List(scopeTemplateRead_2.copy(apiKeyTemplateId = templateId))
            inputScopeTemplates = List(scopeTemplateRead_1.copy(apiKeyTemplateId = templateId))

            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

            res <- scopeTemplateDb.insertMany(inputScopeTemplates)
          } yield (inputScopeTemplates, res)).transact(transactor)

          result.asserting { case (inputScopeTemplates, res) =>
            res.value.map(_.toWrite) shouldBe inputScopeTemplates
          }
        }

        "insert entity into DB" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            oldScopeTemplates = List(scopeTemplateRead_2.copy(apiKeyTemplateId = templateId))
            inputScopeTemplates = List(scopeTemplateRead_1.copy(apiKeyTemplateId = templateId))

            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

            res <- Queries.getAllScopes.compile.toList
            allScopeTemplates = oldScopeTemplates ++ inputScopeTemplates
          } yield (allScopeTemplates, res)).transact(transactor)

          result.asserting { case (allScopeTemplates, res) =>
            res.map(_.toWrite) should contain theSameElementsAs allScopeTemplates
          }
        }
      }
    }

    "provided with multiple scope templates" when {

      def inputScopeTemplates(templateId: Long): List[ScopeTemplateEntity.Write] =
        List(scopeTemplateRead_1, scopeTemplateRead_2, scopeTemplateWrite_1, scopeTemplateWrite_2)
          .map(_.copy(apiKeyTemplateId = templateId))

      "there are no rows in the DB" should {

        "return inserted entities" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            inputScopeTemplatesCorrectTemplateId = inputScopeTemplates(templateId)

            res <- scopeTemplateDb.insertMany(inputScopeTemplatesCorrectTemplateId)
          } yield (inputScopeTemplatesCorrectTemplateId, res)).transact(transactor)

          result.asserting { case (inputScopeTemplatesCorrectTemplateId, res) =>
            res.value.map(_.toWrite) should contain theSameElementsAs inputScopeTemplatesCorrectTemplateId
          }
        }

        "insert entities into DB" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            inputScopeTemplatesCorrectTemplateId = inputScopeTemplates(templateId)

            _ <- scopeTemplateDb.insertMany(inputScopeTemplatesCorrectTemplateId)

            res <- Queries.getAllScopes.compile.toList
          } yield (inputScopeTemplatesCorrectTemplateId, res)).transact(transactor)

          result.asserting { case (inputScopeTemplatesCorrectTemplateId, res) =>
            res.map(_.toWrite) should contain theSameElementsAs inputScopeTemplatesCorrectTemplateId
          }
        }
      }

      "there are rows in the DB with the same apiKeyTemplateId and value as input" should {

        val oldScopeTemplate_1 = scopeTemplateRead_3.copy(value = scopeRead_2)
        val oldScopeTemplate_2 = scopeTemplateWrite_3.copy(value = scopeWrite_2)
        def oldScopeTemplates(templateId: Long): List[ScopeTemplateEntity.Write] =
          List(oldScopeTemplate_1, oldScopeTemplate_2).map(_.copy(apiKeyTemplateId = templateId))

        "return Left containing ScopeTemplateAlreadyExistsError" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            oldScopeTemplatesCorrectTemplateId = oldScopeTemplates(templateId)
            inputScopeTemplatesCorrectTemplateId = inputScopeTemplates(templateId)

            _ <- scopeTemplateDb.insertMany(oldScopeTemplatesCorrectTemplateId)

            res <- scopeTemplateDb.insertMany(inputScopeTemplatesCorrectTemplateId)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe Left(ScopeTemplateAlreadyExistsError))
        }

        "NOT insert the new entities into DB" in {
          val result = for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1).transact(transactor)
            oldScopeTemplatesCorrectTemplateId = oldScopeTemplates(templateId)
            inputScopeTemplatesCorrectTemplateId = inputScopeTemplates(templateId)

            _ <- scopeTemplateDb.insertMany(oldScopeTemplatesCorrectTemplateId).transact(transactor)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplatesCorrectTemplateId).transact(transactor)

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield (oldScopeTemplatesCorrectTemplateId, res)

          result.asserting { case (oldScopeTemplatesCorrectTemplateId, res) =>
            res.map(_.toWrite) should contain theSameElementsAs oldScopeTemplatesCorrectTemplateId
          }
        }
      }

      "there are different rows in the DB" should {

        def oldScopeTemplates(templateId: Long): List[ScopeTemplateEntity.Write] =
          List(scopeTemplateRead_3, scopeTemplateWrite_3).map(_.copy(apiKeyTemplateId = templateId))

        "return inserted entities" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            oldScopeTemplatesCorrectTemplateId = oldScopeTemplates(templateId)
            inputScopeTemplatesCorrectTemplateId = inputScopeTemplates(templateId)

            _ <- scopeTemplateDb.insertMany(oldScopeTemplatesCorrectTemplateId)

            res <- scopeTemplateDb.insertMany(inputScopeTemplatesCorrectTemplateId)
          } yield (inputScopeTemplatesCorrectTemplateId, res)).transact(transactor)

          result.asserting { case (inputScopeTemplatesCorrectTemplateId, res) =>
            res.value.map(_.toWrite) should contain theSameElementsAs inputScopeTemplatesCorrectTemplateId
          }
        }

        "insert entities into DB" in {
          val result = (for {
            templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
            oldScopeTemplatesCorrectTemplateId = oldScopeTemplates(templateId)
            inputScopeTemplatesCorrectTemplateId = inputScopeTemplates(templateId)

            _ <- scopeTemplateDb.insertMany(oldScopeTemplatesCorrectTemplateId)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplatesCorrectTemplateId)

            res <- Queries.getAllScopes.compile.toList
            allScopeTemplates = oldScopeTemplatesCorrectTemplateId ++ inputScopeTemplatesCorrectTemplateId
          } yield (allScopeTemplates, res)).transact(transactor)

          result.asserting { case (allScopeTemplates, res) =>
            res.map(_.toWrite) should contain theSameElementsAs allScopeTemplates
          }
        }
      }
    }
  }

  "ScopeTemplateDb on getByApiKeyTemplateId" when {

    "there are no rows in the DB" should {
      "return empty Stream" in {
        scopeTemplateDb.getByApiKeyTemplateId(123L).transact(transactor).compile.toList.asserting(_ shouldBe List.empty)
      }
    }

    "there are rows in the DB with different API Key Template ID" should {
      "return empty Stream" in {
        val result = (for {
          templateId <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
          oldScopeTemplates = List(scopeTemplateRead_1, scopeTemplateRead_2, scopeTemplateWrite_1, scopeTemplateWrite_2)
            .map(_.copy(apiKeyTemplateId = templateId))

          _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

          res <- scopeTemplateDb.getByApiKeyTemplateId(124L).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are rows in the DB with the same API Key Template ID" should {
      "return only these rows" in {
        val result = (for {
          templateId_1 <- insertPrerequisiteData(apiKeyTemplateEntityWrite_1)
          templateId_2 <- insertPrerequisiteData(apiKeyTemplateEntityWrite_2)
          oldScopeTemplates = List(
            scopeTemplateRead_1.copy(apiKeyTemplateId = templateId_1),
            scopeTemplateWrite_1.copy(apiKeyTemplateId = templateId_1),
            scopeTemplateRead_2.copy(apiKeyTemplateId = templateId_2),
            scopeTemplateWrite_2.copy(apiKeyTemplateId = templateId_2),
            scopeTemplateRead_3.copy(apiKeyTemplateId = templateId_2),
            scopeTemplateWrite_3.copy(apiKeyTemplateId = templateId_2)
          )

          _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

          res <- scopeTemplateDb.getByApiKeyTemplateId(templateId_1).compile.toList
        } yield (templateId_1, res)).transact(transactor)

        result.asserting { case (templateId, res) =>
          val expectedResult =
            List(scopeTemplateRead_1, scopeTemplateWrite_1).map(_.copy(apiKeyTemplateId = templateId))

          res.map(_.toWrite) shouldBe expectedResult
        }
      }
    }
  }
}
