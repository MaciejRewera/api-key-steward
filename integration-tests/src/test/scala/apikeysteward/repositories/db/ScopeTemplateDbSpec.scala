package apikeysteward.repositories.db

import apikeysteward.base.IntegrationTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.DbCommons.ScopeTemplateInsertionError.ScopeTemplateAlreadyExistsError
import apikeysteward.repositories.db.entity.ScopeTemplateEntity
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
    _ <- sql"TRUNCATE scope_template CASCADE".update.run
  } yield ()

  private val scopeTemplateDb = new ScopeTemplateDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllScopes: Stream[doobie.ConnectionIO, ScopeTemplateEntity.Read] =
      sql"SELECT * FROM scope_template".query[ScopeTemplateEntity.Read].stream
  }

  private implicit class ScopeTemplateEntityReadToWrite(scopeTemplateEntityRead: ScopeTemplateEntity.Read) {
    def toWrite: ScopeTemplateEntity.Write =
      ScopeTemplateEntity.Write(
        apiKeyTemplateId = 123L,
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
        scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor).asserting(_.value shouldBe empty)
      }

      "NOT insert anything into DB" in {
        val result = (for {
          _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

          res <- Queries.getAllScopes.compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe empty)
      }
    }

    "provided with a single scope" when {

      val inputScopeTemplates = List(scopeTemplateRead_1)

      "there are no rows in the DB" should {

        "return inserted entity" in {
          scopeTemplateDb
            .insertMany(inputScopeTemplates)
            .transact(transactor)
            .asserting(_.value.map(_.toWrite) shouldBe List(scopeTemplateRead_1))
        }

        "insert entity into DB" in {
          val result = (for {
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

            res <- Queries.getAllScopes.compile.toList
          } yield res).transact(transactor)

          result.asserting(_.map(_.toWrite) shouldBe List(scopeTemplateRead_1))
        }
      }

      "there is a row with the same apiKeyTemplateId and value in the DB" should {

        val oldScopeTemplate = scopeTemplateRead_2.copy(value = scopeRead_1)
        val oldScopeTemplates = List(oldScopeTemplate)

        "return Left containing ScopeTemplateAlreadyExistsError" in {
          val result = (for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

            res <- scopeTemplateDb.insertMany(inputScopeTemplates)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe Left(ScopeTemplateAlreadyExistsError))
        }

        "NOT insert the new entity into DB" in {
          val result = for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates).transact(transactor)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor)

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield res

          result.asserting(_.map(_.toWrite) shouldBe oldScopeTemplates)
        }
      }

      "there is a different row in the DB" should {

        val oldScopeTemplate = scopeTemplateRead_2
        val oldScopeTemplates = List(oldScopeTemplate)

        "return inserted entity" in {
          val result = (for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

            res <- scopeTemplateDb.insertMany(inputScopeTemplates)
          } yield res).transact(transactor)

          result.asserting(_.value.map(_.toWrite) shouldBe List(scopeTemplateRead_1))
        }

        "insert entity into DB" in {
          val result = (for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

            res <- Queries.getAllScopes.compile.toList
          } yield res).transact(transactor)

          result.asserting(
            _.map(_.toWrite) should contain theSameElementsAs List(oldScopeTemplate, scopeTemplateRead_1)
          )
        }
      }
    }

    "provided with multiple scope templates" when {

      val inputScopeTemplates =
        List(scopeTemplateRead_1, scopeTemplateRead_2, scopeTemplateWrite_1, scopeTemplateWrite_2)

      "there are no rows in the DB" should {

        "return inserted entities" in {
          scopeTemplateDb
            .insertMany(inputScopeTemplates)
            .transact(transactor)
            .asserting(_.value.map(_.toWrite) should contain theSameElementsAs inputScopeTemplates)
        }

        "insert entities into DB" in {
          val result = (for {
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates)

            res <- Queries.getAllScopes.compile.toList
          } yield res).transact(transactor)

          result.asserting(_.map(_.toWrite) should contain theSameElementsAs inputScopeTemplates)
        }
      }

      "there are rows in the DB with the same apiKeyTemplateId and value as input" should {

        val oldScopeTemplate_1 = scopeTemplateRead_3.copy(value = scopeRead_2)
        val oldScopeTemplate_2 = scopeTemplateWrite_3.copy(value = scopeWrite_2)
        val oldScopeTemplates = List(oldScopeTemplate_1, oldScopeTemplate_2)

        "return Left containing ScopeTemplateAlreadyExistsError" in {
          val result = (for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

            res <- scopeTemplateDb.insertMany(inputScopeTemplates)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe Left(ScopeTemplateAlreadyExistsError))
        }

        "NOT insert the new entities into DB" in {
          val result = for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates).transact(transactor)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor)

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield res

          result.asserting(_.map(_.toWrite) should contain theSameElementsAs oldScopeTemplates)
        }
      }

      "there are different rows in the DB" should {

        val oldScopeTemplates = List(scopeTemplateRead_3, scopeTemplateWrite_3)

        "return inserted entities" in {
          val result = for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates).transact(transactor)

            res <- scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor)
          } yield res

          result.asserting(_.value.map(_.toWrite) should contain theSameElementsAs inputScopeTemplates)
        }

        "insert entities into DB" in {
          val result = for {
            _ <- scopeTemplateDb.insertMany(oldScopeTemplates).transact(transactor)
            _ <- scopeTemplateDb.insertMany(inputScopeTemplates).transact(transactor)

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield res

          result.asserting(_.map(_.toWrite) should contain theSameElementsAs oldScopeTemplates ++ inputScopeTemplates)
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

    "there are rows in the DB with a different API Key Template ID" should {
      "return empty Stream" in {
        val oldScopeTemplates =
          List(scopeTemplateRead_1, scopeTemplateRead_2, scopeTemplateWrite_1, scopeTemplateWrite_2)

        val result = (for {
          _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

          res <- scopeTemplateDb.getByApiKeyTemplateId(124L).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are rows in the DB with the same API Key Template ID" should {
      "return only these rows" in {
        val oldScopeTemplates = List(
          scopeTemplateRead_1,
          scopeTemplateRead_2.copy(apiKeyTemplateId = 124L),
          scopeTemplateRead_3.copy(apiKeyTemplateId = 125L),
          scopeTemplateWrite_1,
          scopeTemplateWrite_2.copy(apiKeyTemplateId = 124L),
          scopeTemplateWrite_3.copy(apiKeyTemplateId = 125L)
        )

        val result = (for {
          _ <- scopeTemplateDb.insertMany(oldScopeTemplates)

          res <- scopeTemplateDb.getByApiKeyTemplateId(123L).compile.toList
        } yield res).transact(transactor)

        result.asserting(_.map(_.toWrite) shouldBe List(scopeTemplateRead_1, scopeTemplateWrite_1))
      }
    }
  }
}
