package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.apiKeyTemplateEntityWrite_1
import apikeysteward.base.testdata.TenantsTestData.tenantEntityWrite_1
import apikeysteward.base.testdata.UsersTestData.userEntityWrite_1
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersNotFoundError
import apikeysteward.repositories.TestDataInsertions.{TemplateDbId, TenantDbId, UserDbId}
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyTemplatesUsersDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <-
      sql"TRUNCATE tenant, tenant_user, api_key_template, api_key_templates_users CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val userDb = new UserDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb

  private val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllAssociations: ConnectionIO[List[ApiKeyTemplatesUsersEntity.Read]] =
      sql"SELECT * FROM api_key_templates_users"
        .query[ApiKeyTemplatesUsersEntity.Read]
        .stream
        .compile
        .toList
  }

  private def insertPrerequisiteData(): ConnectionIO[(TenantDbId, List[TemplateDbId], List[UserDbId])] =
    TestDataInsertions.insertPrerequisiteTemplatesAndUsers(tenantDb, userDb, apiKeyTemplateDb)

  private def convertEntitiesWriteToRead(
      entitiesWrite: List[ApiKeyTemplatesUsersEntity.Write]
  ): List[ApiKeyTemplatesUsersEntity.Read] =
    entitiesWrite.map { entityWrite =>
      ApiKeyTemplatesUsersEntity.Read(
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        userId = entityWrite.userId
      )
    }

  "ApiKeyTemplatesUsersDb on insertMany" when {

    "there are no rows in the DB" should {

      "return inserted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (allEntities, entitiesToInsert) =>
          allEntities.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided apiKeyTemplateId, but different userId" should {

      "return inserted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(2))
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(2))
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with a different apiKeyTemplateId, but provided userId" should {

      "return inserted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds.head)
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds.head)
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided both apiKeyTemplateId and userId" should {

      "return Left containing ApiKeyTemplatesUsersAlreadyExistsError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, templateIds.head, userIds.head)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesUsersAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into DB" in {
        val result = for {
          dataIds <- insertPrerequisiteData().transact(transactor)
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (res, preExistingEntities) =>
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          res shouldBe expectedEntities
        }
      }
    }

    "there is ApiKeyTemplate but NO Users in the DB" should {

      "return Left containing ReferencedUserDoesNotExistError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb
            .insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = 101L),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = 102L),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = 103L)
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(101L)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          templateId <- apiKeyTemplateDb
            .insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = 101L),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = 102L),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = 103L)
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is User but NO ApiKeyTemplates in the DB" should {

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          userId <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = 101L, userId = userId),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = 102L, userId = userId),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = 103L, userId = userId)
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(101L)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          userId <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id).transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = 101L, userId = userId),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = 102L, userId = userId),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = 103L, userId = userId)
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is an exception returned for one of the subsequent entities" should {

      "return Left containing appropriate ApiKeyTemplatesUsersInsertionError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(2))
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, templateIds.head, userIds.head)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesUsersAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          dataIds <- insertPrerequisiteData().transact(transactor)
          (_, userIds, templateIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(2))
          )

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (res, preExistingEntities) =>
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          res shouldBe expectedEntities
        }
      }
    }
  }

  "ApiKeyTemplatesUsersDb on deleteMany" when {

    "provided with an empty List" should {

      "return Right containing empty List" in {
        apiKeyTemplatesUsersDb.deleteMany(List.empty).transact(transactor).asserting(_ shouldBe Right(List.empty))
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          entitiesToDelete = List.empty

          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, entitiesExpectedNotToBeDeleted)).transact(transactor)

        result.asserting { case (allEntities, entitiesExpectedNotToBeDeleted) =>
          allEntities.size shouldBe 2
          val expectedEntities = convertEntitiesWriteToRead(entitiesExpectedNotToBeDeleted)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are no rows in the DB" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there is a row in the DB with provided apiKeyTemplateId, but different userId" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1))
          )

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1))
          )

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with a different apiKeyTemplateId, but provided userId" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
          )

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
          )

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are rows in the DB with provided both apiKeyTemplateId and userId" should {

      "return Right containing deleted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(2))
          )
          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete)).transact(transactor)

        result.asserting { case (res, entitiesToDelete) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToDelete)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "delete these rows from the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(2))
          )
          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, entitiesExpectedNotToBeDeleted)).transact(transactor)

        result.asserting { case (allEntities, entitiesExpectedNotToBeDeleted) =>
          allEntities.size shouldBe 2
          val expectedEntities = convertEntitiesWriteToRead(entitiesExpectedNotToBeDeleted)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is an exception returned for one of the subsequent entities" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(2)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds(1))
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(2))
          )

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.last)).transact(transactor)

        result.asserting { case (res, incorrectEntityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(incorrectEntityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, templateIds, userIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(2)),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(2), userId = userIds(1))
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(2))
          )

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, entitiesExpectedNotToBeDeleted) =>
          allEntities.size shouldBe 5
          val expectedEntities = convertEntitiesWriteToRead(entitiesExpectedNotToBeDeleted)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

}
