package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.IntegrationTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.DbCommons.ApiKeyInsertionError._
import apikeysteward.repositories.db.entity.{ApiKeyDataDeletedEntity, ApiKeyDataEntity}
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyDataDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE api_key, api_key_data, api_key_data_deleted CASCADE".update.run
  } yield ()

  private val apiKeyDb = new ApiKeyDb
  private val apiKeyDataDb = new ApiKeyDataDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApiKeysData: doobie.ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList

    val getAllDeletedApiKeysData: doobie.ConnectionIO[List[ApiKeyDataDeletedEntity.Read]] =
      sql"SELECT * FROM api_key_data_deleted".query[ApiKeyDataDeletedEntity.Read].stream.compile.toList
  }

  "ApiKeyDataDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
        } yield res).transact(transactor)

        result.asserting { res =>
          res.isRight shouldBe true
          res.value shouldBe apiKeyDataEntityRead_1.copy(id = res.value.id, apiKeyId = 1L)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
          resApiKeysData <- Queries.getAllApiKeysData
        } yield resApiKeysData).transact(transactor)

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 1

          val resApiKeyData = resApiKeysData.head
          resApiKeyData shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData.id,
            apiKeyId = resApiKeyData.apiKeyId
          )
        }
      }
    }

    "there is a row in the DB with the same data, but different apiKeyId and publicKeyId" should {

      "return inserted entity" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))
          inserted <- apiKeyDataDb.insert(
            apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2, publicKeyId = publicKeyIdStr_2)
          )
        } yield inserted).transact(transactor)

        result.asserting { res =>
          res.isRight shouldBe true
          res.value shouldBe apiKeyDataEntityRead_1.copy(
            id = res.value.id,
            apiKeyId = res.value.apiKeyId,
            publicKeyId = publicKeyIdStr_2
          )
        }
      }

      "insert entity into DB" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2, publicKeyId = publicKeyIdStr_2))
          resApiKeysData <- Queries.getAllApiKeysData
        } yield resApiKeysData).transact(transactor)

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 2

          val resApiKeyData_1 = resApiKeysData.head
          resApiKeysData.head shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData_1.id,
            apiKeyId = resApiKeyData_1.apiKeyId
          )

          val resApiKeyData_2 = resApiKeysData(1)
          resApiKeysData(1) shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData_2.id,
            apiKeyId = resApiKeyData_2.apiKeyId,
            publicKeyId = publicKeyIdStr_2
          )
        }
      }
    }

    "there is a row in the DB with the same apiKeyId" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId, publicKeyId = publicKeyIdStr_2))
        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe ApiKeyIdAlreadyExistsError
          exc.left.value.message shouldBe "API Key Data with the same apiKeyId already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId)).transact(transactor)
          _ <- apiKeyDataDb
            .insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId, publicKeyId = publicKeyIdStr_2))
            .transact(transactor)
            .attempt

          resApiKeysData <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeysData

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 1

          val resApiKeyData = resApiKeysData.head
          resApiKeysData.head shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData.id,
            apiKeyId = resApiKeyData.apiKeyId
          )
        }
      }
    }

    "there is a row in the DB with the same publicKeyId" should {

      "return Left containing ApiKeyInsertionError" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))
          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2))

        } yield res).transact(transactor)

        result.asserting { exc =>
          exc.isLeft shouldBe true
          exc.left.value shouldBe PublicKeyIdAlreadyExistsError
          exc.left.value.message shouldBe "API Key Data with the same publicKeyId already exists."
        }
      }

      "NOT insert the second entity into the DB" in {
        val result = for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id).transact(transactor)
          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id).transact(transactor)

          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1)).transact(transactor)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2)).transact(transactor).attempt

          resApiKeysData <- Queries.getAllApiKeysData.transact(transactor)
        } yield resApiKeysData

        result.asserting { resApiKeysData =>
          resApiKeysData.size shouldBe 1

          val resApiKeyData = resApiKeysData.head
          resApiKeysData.head shouldBe apiKeyDataEntityRead_1.copy(
            id = resApiKeyData.id,
            apiKeyId = resApiKeyData.apiKeyId
          )
        }
      }
    }
  }

  "ApiKeyDataDb on getByApiKeyId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = apiKeyDataDb.getByApiKeyId(123L).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different apiKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByApiKeyId(apiKeyId + 1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same apiKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByApiKeyId(apiKeyId)
        } yield (res, apiKeyId)).transact(transactor)

        result.asserting { case (res, apiKeyId) =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = apiKeyId)
        }
      }
    }
  }

  "ApiKeyDataDb on getByUserId" when {

    "there are no rows in the DB" should {
      "return empty Stream" in {
        val result = apiKeyDataDb.getByUserId(testUserId_1).compile.toList.transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with different userId" should {
      "return empty Stream" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByUserId(testUserId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same userId" should {
      "return Stream containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByUserId(testUserId_1).compile.toList
        } yield (res, apiKeyId)).transact(transactor)

        result.asserting { case (res, apiKeyId) =>
          res.size shouldBe 1
          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = apiKeyId)
        }
      }
    }

    "there are several rows in the DB with the same userId together with rows with different userIds" should {
      "return Stream containing all matching ApiKeyDataEntities" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2, userId = testUserId_1))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3))

          res <- apiKeyDataDb.getByUserId(testUserId_1).compile.toList
        } yield (res, apiKeyId_1, apiKeyId_2)).transact(transactor)

        result.asserting { case (res, apiKeyId_1, apiKeyId_2) =>
          res.size shouldBe 2
          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = apiKeyId_1)
          res(1) shouldBe apiKeyDataEntityRead_2.copy(id = res(1).id, apiKeyId = apiKeyId_2, userId = testUserId_1)
        }
      }
    }
  }

  "ApiKeyDataDb on getBy(:userId, :publicKeyId)" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = apiKeyDataDb.getBy(testUserId_1, publicKeyId_1).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different both userId and publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(testUserId_2, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same userId but different publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(testUserId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same publicKeyId but different userId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(testUserId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same both userId and publicKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(testUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = res.get.apiKeyId)
        }
      }
    }
  }

  "ApiKeyDataDb on getAllUserIds" when {

    "there are no rows in the DB" should {
      "return empty List" in {
        val result = apiKeyDataDb.getAllUserIds.compile.toList.transact(transactor)

        result.asserting(_ shouldBe List.empty[String])
      }
    }

    "there is a single row in the DB" should {
      "return userId of this single row" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getAllUserIds.compile.toList
        } yield res).transact(transactor)

        result.asserting { result =>
          result.size shouldBe 1
          result.head shouldBe testUserId_1
        }
      }
    }

    "there are several rows in the DB with the same userId" should {
      "return single userId" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2, userId = testUserId_1))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3, userId = testUserId_1))

          res <- apiKeyDataDb.getAllUserIds.compile.toList
        } yield res).transact(transactor)

        result.asserting { result =>
          result.size shouldBe 1
          result.head shouldBe testUserId_1
        }
      }
    }

    "there are several rows in the DB with different userIds" should {
      "return all distinct userIds" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3, userId = testUserId_1))

          res <- apiKeyDataDb.getAllUserIds.compile.toList
        } yield res).transact(transactor)

        result.asserting { result =>
          result.size shouldBe 2
          result should contain theSameElementsAs List(testUserId_1, testUserId_2)
        }
      }
    }
  }

  "ApiKeyDataDb on copyIntoDeletedTable" when {

    def buildApiKeyDataDeletedEntityRead(
        id: Long,
        apiKeyDataEntityRead: ApiKeyDataEntity.Read
    ): ApiKeyDataDeletedEntity.Read =
      ApiKeyDataDeletedEntity.Read(
        id = id,
        deletedAt = now,
        apiKeyDataId = apiKeyDataEntityRead.id,
        apiKeyId = apiKeyDataEntityRead.apiKeyId,
        publicKeyId = apiKeyDataEntityRead.publicKeyId,
        name = apiKeyDataEntityRead.name,
        description = apiKeyDataEntityRead.description,
        userId = apiKeyDataEntityRead.userId,
        expiresAt = apiKeyDataEntityRead.expiresAt,
        createdAt = apiKeyDataEntityRead.createdAt,
        updatedAt = apiKeyDataEntityRead.updatedAt
      )

    "there are no rows in the API Key Data table" should {

      "return empty Option" in {
        apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1).transact(transactor).asserting(_ shouldBe None)
      }

      "NOT make any insertions into table with deleted rows" in {
        val result = (for {
          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)
          res <- Queries.getAllDeletedApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataDeletedEntity.Read])
      }
    }

    "there is a row in the API Key Data table with different userId" should {

      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.copyIntoDeletedTable(testUserId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }

      "NOT make any insertions into table with deleted rows" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_2, publicKeyId_1)

          res <- Queries.getAllDeletedApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataDeletedEntity.Read])
      }
    }

    "there is a row in the API Key Data table with different publicKeyId" should {

      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }

      "NOT make any insertions into table with deleted rows" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_2)

          res <- Queries.getAllDeletedApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataDeletedEntity.Read])
      }
    }

    "there is a row in the API Key Data table with the same userId and publicKeyId" should {

      "return copied entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = res.get.apiKeyId)
        }
      }

      "insert the same row into table with deleted rows" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(apiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          apiKeyDataEntityRead <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
          apiKeyDataId = apiKeyDataEntityRead.value.id

          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)

          res <- Queries.getAllDeletedApiKeysData
        } yield (apiKeyId, apiKeyDataId, res)).transact(transactor)

        result.asserting { case (apiKeyId, apiKeyDataId, res) =>
          res.size shouldBe 1

          val expectedDeletedEntityRead =
            buildApiKeyDataDeletedEntityRead(id = res.head.id, apiKeyDataEntityRead_1).copy(
              apiKeyDataId = apiKeyDataId,
              apiKeyId = apiKeyId
            )

          res.head shouldBe expectedDeletedEntityRead
        }
      }
    }

    "there is a row in the API Key Data table with the same userId and publicKeyId, and it is copied for the second time into the API Key Deleted table" should {

      "return copied entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)
          res <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = res.get.apiKeyId)
        }
      }

      "insert this row into table with deleted rows" in {
        val result = (for {
          apiKeyEntityRead <- apiKeyDb.insert(apiKeyEntityWrite_1)
          apiKeyId = apiKeyEntityRead.value.id
          apiKeyDataEntityRead <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
          apiKeyDataId = apiKeyDataEntityRead.value.id

          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)
          _ <- apiKeyDataDb.copyIntoDeletedTable(testUserId_1, publicKeyId_1)

          res <- Queries.getAllDeletedApiKeysData
        } yield (apiKeyId, apiKeyDataId, res)).transact(transactor)

        result.asserting { case (apiKeyId, apiKeyDataId, res) =>
          res.size shouldBe 2

          val expectedDeletedEntityRead =
            buildApiKeyDataDeletedEntityRead(id = res.head.id, apiKeyDataEntityRead_1).copy(
              apiKeyDataId = apiKeyDataId,
              apiKeyId = apiKeyId
            )

          res.head shouldBe expectedDeletedEntityRead.copy(id = res.head.id)
          res(1) shouldBe expectedDeletedEntityRead.copy(id = res(1).id)
        }
      }
    }
  }

  "ApiKeyDataDb on delete" when {

    "there are no rows in the API Key Data table" should {

      "return empty Option" in {
        apiKeyDataDb.delete(testUserId_1, publicKeyId_1).transact(transactor).asserting(_ shouldBe None)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDataDb.delete(testUserId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the API Key Data table with different userId" should {

      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(testUserId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }

      "make no changes to the DB" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.delete(testUserId_2, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = apiKeyDataEntityRead_1
          res.head shouldBe expectedEntity.copy(id = res.head.id, apiKeyId = res.head.apiKeyId)
        }
      }
    }

    "there is a row in the API Key Data table with different publicKeyId" should {

      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(testUserId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }

      "make no changes to the DB" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.delete(testUserId_1, publicKeyId_2)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = res.head.apiKeyId)
        }
      }
    }

    "there is a row in the API Key Data table with the given userId and publicKeyId" should {

      "return deleted entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(testUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = res.get.apiKeyId)
        }
      }

      "delete this row from the API Key Data table" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.delete(testUserId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are several rows in the API Key Data table but only one with the given userId and publicKeyId" should {

      "return deleted entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(testUserId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe defined
          res.get shouldBe apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = res.get.apiKeyId)
        }
      }

      "delete this row from the API Key Data table and leave others intact" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.delete(testUserId_1, publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 2

          res.head shouldBe apiKeyDataEntityRead_2.copy(id = res.head.id, apiKeyId = res.head.apiKeyId)
          res(1) shouldBe apiKeyDataEntityRead_3.copy(id = res(1).id, apiKeyId = res(1).apiKeyId)
        }
      }
    }
  }
}
