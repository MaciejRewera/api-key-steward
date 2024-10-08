package apikeysteward.repositories.db

import apikeysteward.base.IntegrationTestData.ApiKeys._
import apikeysteward.base.TestData.ApiKeys
import apikeysteward.base.TestData.ApiKeys._
import apikeysteward.base.{FixedClock, TestData}
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyDataNotFoundError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
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
    _ <- sql"TRUNCATE api_key, api_key_data CASCADE".update.run
  } yield ()

  private val apiKeyDb = new ApiKeyDb
  private val apiKeyDataDb = new ApiKeyDataDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApiKeysData: doobie.ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList
  }

  "ApiKeyDataDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)

          res <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))
        } yield res).transact(transactor)

        result.asserting(res =>
          res shouldBe Right(apiKeyDataEntityRead_1.copy(id = res.value.id, apiKeyId = res.value.apiKeyId))
        )
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

    "there is a row in the DB with the same data, but different both apiKeyId and publicKeyId" should {

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
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_2)).transact(transactor)

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

    "there is no ApiKey with provided apiKeyId in the DB" should {

      "return Left containing ReferencedApiKeyDoesNotExistError" in {
        apiKeyDataDb
          .insert(apiKeyDataEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedApiKeyDoesNotExistError(apiKeyDataEntityWrite_1.apiKeyId)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeysData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }
  }

  "ApiKeyDataDb on update" when {

    val updatedEntityRead = apiKeyDataEntityRead_1.copy(
      name = ApiKeys.nameUpdated,
      description = ApiKeys.descriptionUpdated,
      updatedAt = nowInstant
    )

    "there are no rows in the API Key Data table" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb
          .update(apiKeyDataEntityUpdate_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the API Key Data table with different publicKeyId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1.copy(publicKeyId = publicKeyIdStr_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1.copy(publicKeyId = publicKeyIdStr_2))
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = res.head.apiKeyId)
          res.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the API Key Data table with given userId and publicKeyId" should {

      "return Right containing updated entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, apiKeyId = res.value.apiKeyId))
        }
      }

      "update this row" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe updatedEntityRead.copy(id = res.head.id, apiKeyId = res.head.apiKeyId)
        }
      }
    }

    "there are several rows in the API Key Data table but only one with given userId and publicKeyId" should {

      "return Right containing updated entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, apiKeyId = res.value.apiKeyId))
        }
      }

      "update only this row and leave others unchanged" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          entityRead_1 <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          entityRead_2 <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          entityRead_3 <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3))

          _ <- apiKeyDataDb.update(apiKeyDataEntityUpdate_1)
          res <- Queries.getAllApiKeysData
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_1, entityRead_2, entityRead_3) =>
          res.size shouldBe 3

          val expectedEntities = Seq(
            updatedEntityRead.copy(id = entityRead_1.id, apiKeyId = entityRead_1.apiKeyId),
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
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
          res shouldBe Some(apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = apiKeyId))
        }
      }
    }
  }

  "ApiKeyDataDb on getByUserId" when {

    "there are no rows in the DB" should {
      "return empty Stream" in {
        val result = apiKeyDataDb.getByUserId(userId_1).compile.toList.transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with different userId" should {
      "return empty Stream" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByUserId(userId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same userId" should {
      "return Stream containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByUserId(userId_1).compile.toList
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
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2, userId = userId_1))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3))

          res <- apiKeyDataDb.getByUserId(userId_1).compile.toList
        } yield (res, apiKeyId_1, apiKeyId_2)).transact(transactor)

        result.asserting { case (res, apiKeyId_1, apiKeyId_2) =>
          res.size shouldBe 2
          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = apiKeyId_1)
          res(1) shouldBe apiKeyDataEntityRead_2.copy(id = res(1).id, apiKeyId = apiKeyId_2, userId = userId_1)
        }
      }
    }
  }

  "ApiKeyDataDb on getByPublicKeyId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = apiKeyDataDb.getByPublicKeyId(publicKeyId_1).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with different publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByPublicKeyId(publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the DB with the same publicKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getByPublicKeyId(publicKeyId_1)
        } yield (res, apiKeyId)).transact(transactor)

        result.asserting { case (res, apiKeyId) =>
          res shouldBe Some(apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = apiKeyId))
        }
      }
    }

    "there are several rows in the DB" should {
      "return Option containing ApiKeyDataEntity with the same publicKeyId" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3))

          res <- apiKeyDataDb.getByPublicKeyId(publicKeyId_1)
        } yield (res, apiKeyId_1)).transact(transactor)

        result.asserting { case (res, apiKeyId_1) =>
          res shouldBe Some(apiKeyDataEntityRead_1.copy(id = res.get.id, apiKeyId = apiKeyId_1))
        }
      }
    }
  }

  "ApiKeyDataDb on getBy(:userId, :publicKeyId)" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = apiKeyDataDb.getBy(userId_1, publicKeyId_1).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different both userId and publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(userId_2, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same userId but different publicKeyId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(userId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same publicKeyId but different userId" should {
      "return empty Option" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(userId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same both userId and publicKeyId" should {
      "return Option containing ApiKeyDataEntity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.getBy(userId_1, publicKeyId_1)
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
      "return empty Stream" in {
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
          result.head shouldBe userId_1
        }
      }
    }

    "there are several rows in the DB with the same userId" should {
      "return single userId" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2, userId = userId_1))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3, userId = userId_1))

          res <- apiKeyDataDb.getAllUserIds.compile.toList
        } yield res).transact(transactor)

        result.asserting { result =>
          result.size shouldBe 1
          result.head shouldBe userId_1
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
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3, userId = userId_1))

          res <- apiKeyDataDb.getAllUserIds.compile.toList
        } yield res).transact(transactor)

        result.asserting { result =>
          result.size shouldBe 2
          result should contain theSameElementsAs List(userId_1, userId_2)
        }
      }
    }
  }

  "ApiKeyDataDb on delete(:publicKeyId)" when {

    "there are no rows in the API Key Data table" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb
          .delete(publicKeyId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyDataDb.delete(publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there is a row in the API Key Data table with different publicKeyId" should {

      "return Left containing ApiKeyDataNotFoundError" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.delete(publicKeyId_2)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          res.head shouldBe apiKeyDataEntityRead_1.copy(id = res.head.id, apiKeyId = res.head.apiKeyId)
        }
      }
    }

    "there is a row in the API Key Data table with given publicKeyId" should {

      "return Right containing deleted entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(apiKeyDataEntityRead_1.copy(id = res.value.id, apiKeyId = res.value.apiKeyId))
        }
      }

      "delete this row from the API Key Data table" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          _ <- apiKeyDataDb.delete(publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "there are several rows in the API Key Data table but only one with given publicKeyId" should {

      "return Right containing deleted entity" in {
        val result = (for {
          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId))

          apiKeyId <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId))

          res <- apiKeyDataDb.delete(publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(apiKeyDataEntityRead_1.copy(id = res.value.id, apiKeyId = res.value.apiKeyId))
        }
      }

      "delete this row from the API Key Data table and leave others intact" in {
        val result = (for {
          apiKeyId_1 <- apiKeyDb.insert(apiKeyEntityWrite_1).map(_.value.id)
          _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1.copy(apiKeyId = apiKeyId_1))

          apiKeyId_2 <- apiKeyDb.insert(apiKeyEntityWrite_2).map(_.value.id)
          entityRead_2 <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2.copy(apiKeyId = apiKeyId_2))

          apiKeyId_3 <- apiKeyDb.insert(apiKeyEntityWrite_3).map(_.value.id)
          entityRead_3 <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3.copy(apiKeyId = apiKeyId_3))

          _ <- apiKeyDataDb.delete(publicKeyId_1)
          res <- Queries.getAllApiKeysData
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_2, entityRead_3) =>
          res.size shouldBe 2

          val expectedEntities = Seq(
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }
}
