package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.IntegrationTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity._
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
    with FixedClock
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <-
      sql"TRUNCATE api_key, api_key_data, api_key_data_deleted, scope, api_key_data_scopes, api_key_data_scopes_deleted CASCADE".update.run
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

    val getAllDeleted: doobie.ConnectionIO[List[ApiKeyDataScopesDeletedEntity.Read]] =
      sql"SELECT * FROM  api_key_data_scopes_deleted".query[ApiKeyDataScopesDeletedEntity.Read].stream.compile.toList
  }

  private def insertPrerequisiteData(
      apiKeyEntityWrite: ApiKeyEntity.Write,
      apiKeyDataEntityWrite: ApiKeyDataEntity.Write,
      scopeEntitiesWrite: List[ScopeEntity.Write]
  ): doobie.ConnectionIO[List[ApiKeyDataScopesEntity.Write]] =
    for {
      apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite).map(_.value.id)
      apiKeyDataId <- apiKeyDataDb.insert(apiKeyDataEntityWrite.copy(apiKeyId = apiKeyId)).map(_.value.id)
      scopeIds <- scopeDb.insertMany(scopeEntitiesWrite).map(_.id).compile.toList
      inputs = scopeIds.map(scopeId => ApiKeyDataScopesEntity.Write(apiKeyDataId = apiKeyDataId, scopeId = scopeId))
    } yield inputs

  "ApiKeyDataScopesDb on insertMany" when {

    "provided with NO entities" should {

      val inputEntity = List.empty[ApiKeyDataScopesEntity.Write]

      "return empty Stream" in {
        apiKeyDataScopesDb.insertMany(inputEntity).transact(transactor).asserting(_ shouldBe 0)
      }

      "NOT insert anything into DB" in {
        val result = (for {
          _ <- apiKeyDataScopesDb.insertMany(inputEntity)

          res <- Queries.getAll.compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe empty)
      }
    }

    "provided with a single entity" when {

      val scopeEntitiesWrite = List(scopeRead_1).map(ScopeEntity.Write)

      "there are no entities in the DB" should {

        "return the number of inserted entities" in {
          val result = (for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)

            res <- apiKeyDataScopesDb.insertMany(inputEntities)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe 1)
        }

        "insert entity into DB" in {
          val result = (for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities)

            res <- Queries.getAll.compile.toList
          } yield (inputEntities, res)).transact(transactor)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 1

            allEntitiesInDb.head shouldBe ApiKeyDataScopesEntity.Read(
              apiKeyDataId = inputEntities.head.apiKeyDataId,
              scopeId = inputEntities.head.scopeId,
              createdAt = now,
              updatedAt = now
            )
          }
        }
      }

      "there is the same entity in the DB" should {

        "return duplication exception" in {
          val result = for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
              .transact(
                transactor
              )
            _ <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor)

            res <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor).attempt
          } yield res

          result.asserting { res =>
            res.isLeft shouldBe true
            res.left.value.getMessage should include("ERROR: duplicate key value violates unique constraint")
          }
        }

        "NOT insert the entity into DB" in {
          val result = for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
              .transact(
                transactor
              )
            _ <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor).attempt

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputEntities, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 1

            allEntitiesInDb.head shouldBe ApiKeyDataScopesEntity.Read(
              apiKeyDataId = inputEntities.head.apiKeyDataId,
              scopeId = inputEntities.head.scopeId,
              createdAt = now,
              updatedAt = now
            )
          }
        }
      }

      "there is a different entity in the DB with" should {

        "return the number of inserted entities" in {
          val result = (for {
            inputEntities_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

            inputEntities_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite)

            res <- apiKeyDataScopesDb.insertMany(inputEntities_2)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe 1)
        }

        "insert entity into DB" in {
          val result = (for {
            inputEntities_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

            inputEntities_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities_2)

            res <- Queries.getAll.compile.toList
          } yield (inputEntities_1 ++ inputEntities_2, res)).transact(transactor)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 2

            allEntitiesInDb should contain theSameElementsAs inputEntities.map(e =>
              ApiKeyDataScopesEntity.Read(e.apiKeyDataId, e.scopeId, now, now)
            )
          }
        }
      }
    }

    "provided with multiple entities" when {

      val scopeEntitiesWrite_1 = List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2).map(ScopeEntity.Write)
      val scopeEntitiesWrite_2 = List(scopeRead_1, scopeWrite_1, scopeWrite_2, scopeRead_3).map(ScopeEntity.Write)

      "there are no entities in the DB" should {

        "return the number of inserted entities" in {
          val result = (for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)

            res <- apiKeyDataScopesDb.insertMany(inputEntities)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe 4)
        }

        "insert entities into DB" in {
          val result = (for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities)

            res <- Queries.getAll.compile.toList
          } yield (inputEntities, res)).transact(transactor)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 4

            allEntitiesInDb should contain theSameElementsAs inputEntities.map(e =>
              ApiKeyDataScopesEntity.Read(e.apiKeyDataId, e.scopeId, now, now)
            )
          }
        }
      }

      "there are the same entities in the DB" should {

        "return duplication exception" in {
          val result = for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
              .transact(transactor)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor)

            res <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor).attempt
          } yield res

          result.asserting { res =>
            res.isLeft shouldBe true
            res.left.value.getMessage should include("ERROR: duplicate key value violates unique constraint")
          }
        }

        "NOT insert entities into DB" in {
          val result = for {
            inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
              .transact(transactor)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor)
            _ <- apiKeyDataScopesDb.insertMany(inputEntities).transact(transactor).attempt

            res <- Queries.getAll.transact(transactor).compile.toList
          } yield (inputEntities, res)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 4

            allEntitiesInDb should contain theSameElementsAs inputEntities.map(e =>
              ApiKeyDataScopesEntity.Read(e.apiKeyDataId, e.scopeId, now, now)
            )
          }
        }
      }

      "there are different entities in the DB" should {

        "return the number of inserted entities" in {
          val result = (for {
            inputEntities_1 <- insertPrerequisiteData(
              apiKeyEntityWrite_1,
              apiKeyDataEntityWrite_1,
              scopeEntitiesWrite_1
            )
            _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

            inputEntities_2 <- insertPrerequisiteData(
              apiKeyEntityWrite_2,
              apiKeyDataEntityWrite_2,
              scopeEntitiesWrite_2
            )

            res <- apiKeyDataScopesDb.insertMany(inputEntities_2)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe 4)
        }

        "insert entities into DB" in {
          val result = (for {
            inputEntities_1 <- insertPrerequisiteData(
              apiKeyEntityWrite_1,
              apiKeyDataEntityWrite_1,
              scopeEntitiesWrite_1
            )
            _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

            inputEntities_2 <- insertPrerequisiteData(
              apiKeyEntityWrite_2,
              apiKeyDataEntityWrite_2,
              scopeEntitiesWrite_2
            )
            _ <- apiKeyDataScopesDb.insertMany(inputEntities_2)

            res <- Queries.getAll.compile.toList
          } yield (inputEntities_1 ++ inputEntities_2, res)).transact(transactor)

          result.asserting { case (inputEntities, allEntitiesInDb) =>
            allEntitiesInDb.size shouldBe 8

            allEntitiesInDb should contain theSameElementsAs inputEntities.map(e =>
              ApiKeyDataScopesEntity.Read(e.apiKeyDataId, e.scopeId, now, now)
            )
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
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          apiKeyDataId = 1 + inputEntities.head.apiKeyDataId
          res <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe empty)
      }
    }

    "there is a single entity in the DB with the same apiKeyDataId" should {
      "return Stream containing this entity" in {
        val scopeEntitiesWrite = List(scopeRead_1).map(ScopeEntity.Write)
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          apiKeyDataId = inputEntities.head.apiKeyDataId
          res <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).compile.toList
        } yield (inputEntities, res)).transact(transactor)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 1

          res.head shouldBe ApiKeyDataScopesEntity.Read(
            apiKeyDataId = inputEntities.head.apiKeyDataId,
            scopeId = inputEntities.head.scopeId,
            createdAt = now,
            updatedAt = now
          )
        }
      }
    }

    "there are multiple entities in the DB and some of them have the same apiKeyDataId" should {
      "return Stream containing only entities with the same apiKeyDataId" in {
        val scopeEntitiesWrite_1 = List(scopeRead_1, scopeWrite_1).map(ScopeEntity.Write)
        val scopeEntitiesWrite_2 = List(scopeRead_1, scopeRead_2, scopeWrite_2).map(ScopeEntity.Write)

        val result = (for {
          inputEntities_1 <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeEntitiesWrite_1)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

          inputEntities_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeEntitiesWrite_2)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities_2)

          apiKeyDataId = inputEntities_1.head.apiKeyDataId
          res <- apiKeyDataScopesDb.getByApiKeyDataId(apiKeyDataId).compile.toList
        } yield (inputEntities_1, res)).transact(transactor)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 2

          res should contain theSameElementsAs inputEntities.map(e =>
            ApiKeyDataScopesEntity.Read(e.apiKeyDataId, e.scopeId, now, now)
          )
        }
      }
    }
  }

  "ApiKeyDataScopesDb on copyIntoDeletedTable" when {

    val scopeReadEntityWrite = List(scopeRead_1).map(ScopeEntity.Write)

    "there are no entities in the junction table" should {

      val apiKeyDataId = 101L
      val scopeId = 201L

      "return false" in {
        apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId).transact(transactor).asserting(_ shouldBe false)
      }

      "NOT make any insertions into table with deleted rows" in {
        val result = (for {
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
          res <- Queries.getAllDeleted
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataScopesDeletedEntity.Read])
      }
    }

    "there is an entity in the junction table with different apiKeyDataId" should {

      "return false" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId + 1, inputEntities.head.scopeId)
          res <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe false)
      }

      "NOT make any insertions into table with deleted rows" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId + 1, inputEntities.head.scopeId)
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)

          res <- Queries.getAllDeleted
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataScopesDeletedEntity.Read])
      }
    }

    "there is an entity in the junction table with different scopeId" should {

      "return false" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId + 1)
          res <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe false)
      }

      "NOT make any insertions into table with deleted rows" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId + 1)
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)

          res <- Queries.getAllDeleted
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataScopesDeletedEntity.Read])
      }
    }

    "there is an entity in the junction table with the same both apiKeyDataId and scopeId" should {

      "return true" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId)
          res <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe true)
      }

      "insert the same entity into table with deleted entities" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId)
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)

          res <- Queries.getAllDeleted
        } yield (apiKeyDataId, scopeId, res)).transact(transactor)

        result.asserting { case (apiKeyDataId, scopeId, res) =>
          res.size shouldBe 1
          res.head shouldBe ApiKeyDataScopesDeletedEntity.Read(
            id = res.head.id,
            deletedAt = now,
            apiKeyDataId = apiKeyDataId,
            scopeId = scopeId,
            createdAt = now,
            updatedAt = now
          )
        }
      }
    }

    "there is an entity in both the junction table and the deleted table with the same both apiKeyDataId and scopeId" should {

      "return true" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId)
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
          res <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe true)
      }

      "insert another, same entity into table with deleted entities" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId)
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)
          _ <- apiKeyDataScopesDb.copyIntoDeletedTable(apiKeyDataId, scopeId)

          res <- Queries.getAllDeleted
        } yield (apiKeyDataId, scopeId, res)).transact(transactor)

        result.asserting { case (apiKeyDataId, scopeId, res) =>
          res.size shouldBe 2

          val expectedEntity = ApiKeyDataScopesDeletedEntity.Read(
            id = res.head.id,
            deletedAt = now,
            apiKeyDataId = apiKeyDataId,
            scopeId = scopeId,
            createdAt = now,
            updatedAt = now
          )

          res should contain theSameElementsAs List(expectedEntity, expectedEntity.copy(id = res(1).id))
        }
      }
    }
  }

  "ApiKeyDataScopesDb on delete" when {

    val scopeReadEntityWrite = List(scopeRead_1).map(ScopeEntity.Write)

    "there are no entities in the junction table" should {

      val apiKeyDataId = 101L
      val scopeId = 201L

      "return false" in {
        apiKeyDataScopesDb.delete(apiKeyDataId, scopeId).transact(transactor).asserting(_ shouldBe false)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)
          res <- Queries.getAll.compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataScopesEntity.Read])
      }
    }

    "there is an entity in the junction table with different apiKeyDataId" should {

      "return false" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId + 1, inputEntities.head.scopeId)
          res <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe false)
      }

      "make no changes to the DB" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId + 1, inputEntities.head.scopeId)
          _ <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)

          res <- Queries.getAll.compile.toList
        } yield (inputEntities, res)).transact(transactor)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 1

          res.head shouldBe ApiKeyDataScopesEntity.Read(
            apiKeyDataId = inputEntities.head.apiKeyDataId,
            scopeId = inputEntities.head.scopeId,
            createdAt = now,
            updatedAt = now
          )
        }
      }
    }

    "there is an entity in the junction table with different scopeId" should {

      "return false" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId + 1)
          res <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe false)
      }

      "make no changes to the DB" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId + 1)
          _ <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)

          res <- Queries.getAll.compile.toList
        } yield (inputEntities, res)).transact(transactor)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 1

          res.head shouldBe ApiKeyDataScopesEntity.Read(
            apiKeyDataId = inputEntities.head.apiKeyDataId,
            scopeId = inputEntities.head.scopeId,
            createdAt = now,
            updatedAt = now
          )
        }
      }
    }

    "there is an entity in the junction table with the same both apiKeyDataId and scopeId" should {

      "return true" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId)
          res <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe true)
      }

      "delete this entity from the junction table" in {
        val result = (for {
          inputEntities <- insertPrerequisiteData(apiKeyEntityWrite_1, apiKeyDataEntityWrite_1, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities)

          (apiKeyDataId, scopeId) = (inputEntities.head.apiKeyDataId, inputEntities.head.scopeId)
          _ <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)

          res <- Queries.getAll.compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataScopesEntity.Read])
      }
    }

    "there are multiple entities in the junction table but only one with the same apiKeyDataId and scopeId" should {

      val scopeReadEntitiesWrite = List(scopeWrite_1, scopeRead_2, scopeWrite_2).map(ScopeEntity.Write)

      "return true" in {
        val result = (for {
          inputEntities_1 <- insertPrerequisiteData(
            apiKeyEntityWrite_1,
            apiKeyDataEntityWrite_1,
            scopeReadEntitiesWrite
          )
          _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

          inputEntities_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities_2)

          (apiKeyDataId, scopeId) = (inputEntities_2.head.apiKeyDataId, inputEntities_2.head.scopeId)
          res <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe true)
      }

      "delete this entity from the junction table and leave others intact" in {
        val result = (for {
          inputEntities_1 <- insertPrerequisiteData(
            apiKeyEntityWrite_1,
            apiKeyDataEntityWrite_1,
            scopeReadEntitiesWrite
          )
          _ <- apiKeyDataScopesDb.insertMany(inputEntities_1)

          inputEntities_2 <- insertPrerequisiteData(apiKeyEntityWrite_2, apiKeyDataEntityWrite_2, scopeReadEntityWrite)
          _ <- apiKeyDataScopesDb.insertMany(inputEntities_2)

          (apiKeyDataId, scopeId) = (inputEntities_2.head.apiKeyDataId, inputEntities_2.head.scopeId)
          _ <- apiKeyDataScopesDb.delete(apiKeyDataId, scopeId)

          res <- Queries.getAll.compile.toList
        } yield (inputEntities_1, res)).transact(transactor)

        result.asserting { case (inputEntities, res) =>
          res.size shouldBe 3

          res should contain theSameElementsAs inputEntities.map(e =>
            ApiKeyDataScopesEntity.Read(
              apiKeyDataId = e.apiKeyDataId,
              scopeId = e.scopeId,
              createdAt = now,
              updatedAt = now
            )
          )
        }
      }
    }
  }

}
