package apikeysteward.repositories.db

import apikeysteward.base.IntegrationTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyDataScopesEntity, ApiKeyEntity, ScopeEntity}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import fs2.Stream
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyDataScopesDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE api_key, api_key_data, api_key_data_deleted, scope, api_key_data_scopes CASCADE".update.run
  } yield ()

  private val apiKeyDb = new ApiKeyDb
  private val apiKeyDataDb = new ApiKeyDataDb
  private val scopeDb = new ScopeDb

  private val apiKeyDataScopesDb = new ApiKeyDataScopesDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAll: Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read] =
      sql"SELECT * FROM api_key_data_scopes".query[ApiKeyDataScopesEntity.Read].stream
  }

  private def insertPrerequisiteData(
      apiKeyEntityWrite: ApiKeyEntity.Write,
      apiKeyDataEntityWrite: ApiKeyDataEntity.Write,
      scopeEntitiesWrite: List[ScopeEntity.Write]
  ): IO[List[ApiKeyDataScopesEntity.Write]] =
    (for {
      apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite).map(_.value.id)
      apiKeyDataId <- apiKeyDataDb.insert(apiKeyDataEntityWrite.copy(apiKeyId = apiKeyId)).map(_.value.id)
      scopeIds <- scopeDb.insertMany(scopeEntitiesWrite).map(_.id).compile.toList
      inputs = scopeIds.map(scopeId => ApiKeyDataScopesEntity.Write(apiKeyDataId = apiKeyDataId, scopeId = scopeId))
    } yield inputs).transact(transactor)

  "ApiKeyDataScopesDb on insertMany" when {

    "provided with NO entities" should {

      val input = List.empty[ApiKeyDataScopesEntity.Write]

      "return empty Stream" in {
        apiKeyDataScopesDb.insertMany(input).transact(transactor).asserting(_ shouldBe 0)
      }

      "NOT insert anything into DB" in {
        val result = (for {
          _ <- apiKeyDataScopesDb.insertMany(input)

          res <- Queries.getAll.compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe empty)
      }
    }

    "provided with a single entity" when {

      val scopeEntitiesWrite = List(scopeRead_1).map(ScopeEntity.Write)

      "there are no entities in the DB" should {

        "return the number of inserted entities" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)

            res <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)
          } yield res

          result.asserting(_ shouldBe 1)
        }

        "insert entity into DB" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputs, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 1
            allEntitiesInDb.head.apiKeyDataId shouldBe inputEntities.head.apiKeyDataId
            allEntitiesInDb.head.scopeId shouldBe inputEntities.head.scopeId
          }
        }
      }

      "there is the same entity in the DB" should {

        "return duplication exception" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)

            res <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor).attempt
          } yield res

          result.asserting { res =>
            res.isLeft shouldBe true
            res.left.value.getMessage should include("ERROR: duplicate key value violates unique constraint")
          }
        }

        "NOT insert the entity into DB" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor).attempt

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputs, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 1
            allEntitiesInDb.head.apiKeyDataId shouldBe inputEntities.head.apiKeyDataId
            allEntitiesInDb.head.scopeId shouldBe inputEntities.head.scopeId
          }
        }
      }

      "there is a different entity in the DB with" should {

        "return the number of inserted entities" in {
          val result = for {
            inputs_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputs_1).transact(transactor)

            inputs_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite)

            res <- apiKeyDataScopesDb.insertMany(inputs_2).transact(transactor)
          } yield res

          result.asserting(_ shouldBe 1)
        }

        "insert entity into DB" in {
          val result = for {
            inputs_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputs_1).transact(transactor)

            inputs_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputs_2).transact(transactor)

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputs_1 ++ inputs_2, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 2

            allEntitiesInDb.map(e => (e.apiKeyDataId, e.scopeId)) should contain theSameElementsAs
              inputEntities.map(e => (e.apiKeyDataId, e.scopeId))
          }
        }
      }
    }

    "provided with multiple entities" when {

      val scopeEntitiesWrite_1 = List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2).map(ScopeEntity.Write)
      val scopeEntitiesWrite_2 = List(scopeRead_1, scopeWrite_1, scopeWrite_2, scopeRead_3).map(ScopeEntity.Write)

      "there are no entities in the DB" should {

        "return the number of inserted entities" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)

            res <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)
          } yield res

          result.asserting(_ shouldBe 4)
        }

        "insert entities into DB" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputs, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 4

            allEntitiesInDb.map(e => (e.apiKeyDataId, e.scopeId)) should contain theSameElementsAs
              inputEntities.map(e => (e.apiKeyDataId, e.scopeId))
          }
        }
      }

      "there are the same entities in the DB" should {

        "return duplication exception" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)

            res <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor).attempt
          } yield res

          result.asserting { res =>
            res.isLeft shouldBe true
            res.left.value.getMessage should include("ERROR: duplicate key value violates unique constraint")
          }
        }

        "NOT insert entities into DB" in {
          val result = for {
            inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)
            _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor).attempt

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputs, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 4

            allEntitiesInDb.map(e => (e.apiKeyDataId, e.scopeId)) should contain theSameElementsAs
              inputEntities.map(e => (e.apiKeyDataId, e.scopeId))
          }
        }
      }

      "there are different entities in the DB" should {

        "return the number of inserted entities" in {
          val result = for {
            inputs_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
            _ <- apiKeyDataScopesDb.insertMany(inputs_1).transact(transactor)

            inputs_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite_2)

            res <- apiKeyDataScopesDb.insertMany(inputs_2).transact(transactor)
          } yield res

          result.asserting(_ shouldBe 4)
        }

        "insert entities into DB" in {
          val result = for {
            inputs_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
            _ <- apiKeyDataScopesDb.insertMany(inputs_1).transact(transactor)

            inputs_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite_2)
            _ <- apiKeyDataScopesDb.insertMany(inputs_2).transact(transactor)

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputs_1 ++ inputs_2, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 8

            allEntitiesInDb.map(e => (e.apiKeyDataId, e.scopeId)) should contain theSameElementsAs
              inputEntities.map(e => (e.apiKeyDataId, e.scopeId))
          }
        }
      }
    }
  }

  "ApiKeyDataScopesDb on getByApiKeyDataId" when {

    "there are no entities in the DB" should {
      "return empty Stream" in {
        apiKeyDataScopesDb.getByApiKeyDataId(1L).transact(transactor).compile.toList.asserting(_ shouldBe empty)
      }
    }

    "there is an entity in the DB with different apiKeyDataId" should {
      "return empty Stream" in {
        val scopeEntitiesWrite = List(scopeRead_1).map(ScopeEntity.Write)
        val result = for {
          inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)

          apiKeyDataId = 1 + inputs.head.apiKeyDataId
          res <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).transact(transactor).compile.toList
        } yield res

        result.asserting(_ shouldBe empty)
      }
    }

    "there is a single entity in the DB with the same apiKeyDataId" should {
      "return Stream containing this entity" in {
        val scopeEntitiesWrite = List(scopeRead_1).map(ScopeEntity.Write)
        val result = for {
          inputs <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputs).transact(transactor)

          apiKeyDataId = inputs.head.apiKeyDataId
          res <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).transact(transactor).compile.toList
        } yield (inputs, res)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 1
          res.head.apiKeyDataId shouldBe inputEntities.head.apiKeyDataId
          res.head.scopeId shouldBe inputEntities.head.scopeId
        }
      }
    }

    "there are multiple entities in the DB and some of them have the same apiKeyDataId" should {
      "return Stream containing only entities with the same apiKeyDataId" in {
        val scopeEntitiesWrite_1 = List(scopeRead_1, scopeWrite_1).map(ScopeEntity.Write)
        val scopeEntitiesWrite_2 = List(scopeRead_1, scopeRead_2, scopeWrite_2).map(ScopeEntity.Write)

        val result = for {
          inputs_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
          _ <- apiKeyDataScopesDb.insertMany(inputs_1).transact(transactor)

          inputs_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite_2)
          _ <- apiKeyDataScopesDb.insertMany(inputs_2).transact(transactor)

          apiKeyDataId = inputs_1.head.apiKeyDataId
          res <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).transact(transactor).compile.toList
        } yield (inputs_1, res)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 2

          res.map(e => (e.apiKeyDataId, e.scopeId)) should contain theSameElementsAs
            inputEntities.map(e => (e.apiKeyDataId, e.scopeId))
        }
      }
    }
  }

}
