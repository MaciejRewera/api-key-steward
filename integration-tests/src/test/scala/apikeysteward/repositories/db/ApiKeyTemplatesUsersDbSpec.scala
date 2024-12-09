package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersNotFoundError
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
        tenantId = entityWrite.tenantId,
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        userId = entityWrite.userId
      )
    }

  "ApiKeyTemplatesUsersDb on insertMany" when {

    "provided with an empty List" should {

      "return Right containing empty List" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesUsersDb.insertMany(List.empty)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(List.empty))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesUsersDb.insertMany(List.empty)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val entitiesToInsert = List(apiKeyTemplatesUsersEntityWrite_1_1)

        apiKeyTemplatesUsersDb
          .insertMany(entitiesToInsert)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val entitiesToInsert = List(apiKeyTemplatesUsersEntityWrite_1_1)
        val result = for {
          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert).transact(transactor)

          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToInsert = List(apiKeyTemplatesUsersEntityWrite_1_1.copy(tenantId = tenantDbId_2))

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          entitiesToInsert = List(apiKeyTemplatesUsersEntityWrite_1_1.copy(tenantId = tenantDbId_2))

          _ <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_2_1
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
          _ <- insertPrerequisiteData()

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_2_1
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_3
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_3
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_3_1
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_3_1
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_1
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, templateDbId_1, userDbId_1)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesUsersAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_1
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_3
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedUserDoesNotExistError.fromDbId(userDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_3
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_3_1
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError.fromDbId(templateDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- userDb.insert(userEntityWrite_1).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_3_1
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_3
          )

          res <- apiKeyTemplatesUsersDb.insertMany(entitiesToInsert)
        } yield (res, templateDbId_1, userDbId_1)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesUsersAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          preExistingEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_3
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

  "ApiKeyTemplatesUsersDb on deleteAllForUser" when {

    "there is no Tenant in the DB" should {

      "return zero" in {
        apiKeyTemplatesUsersDb
          .deleteAllForUser(publicTenantId_1, publicUserId_1)
          .transact(transactor)
          .asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there is a row in the DB for provided publicTenantId but a different publicUserId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_2)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB for a different publicTenantId but the same publicUserId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_2, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_2, publicUserId_1)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB for provided both publicTenantId and publicUserId" should {

      "return one" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for provided User" should {

      "return the number of deleted rows" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_3_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_3_2
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_3_1
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_3_2
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId_1, publicUserId_1)
          res <- Queries.getAllAssociations
        } yield (res, entitiesExpectedNotToBeDeleted)).transact(transactor)

        result.asserting { case (allEntities, entitiesExpectedNotToBeDeleted) =>
          allEntities.size shouldBe 2
          val expectedEntities = convertEntitiesWriteToRead(entitiesExpectedNotToBeDeleted)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApiKeyTemplatesUsersDb on deleteAllForApiKeyTemplate" when {

    "there is no Tenant in the DB" should {

      "return zero" in {
        apiKeyTemplatesUsersDb
          .deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          .transact(transactor)
          .asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there is a row in the DB, but for a different publicTenantId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_2, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_2, publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB, but for a different ApiKeyTemplate" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_2)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB for given ApiKeyTemplate" should {

      "return one" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given ApiKeyTemplate" should {

      "return the number of deleted rows" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_3,
            apiKeyTemplatesUsersEntityWrite_2_2,
            apiKeyTemplatesUsersEntityWrite_3_2
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_1_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesUsersEntityWrite_2_2,
            apiKeyTemplatesUsersEntityWrite_3_2
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesUsersDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield (res, entitiesExpectedNotToBeDeleted)).transact(transactor)

        result.asserting { case (allEntities, entitiesExpectedNotToBeDeleted) =>
          allEntities.size shouldBe 2
          val expectedEntities = convertEntitiesWriteToRead(entitiesExpectedNotToBeDeleted)
          allEntities should contain theSameElementsAs expectedEntities
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
          _ <- insertPrerequisiteData()

          entitiesToDelete = List.empty

          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_3_2
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

    "there is no Tenant in the DB" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val entitiesToDelete = List(apiKeyTemplatesUsersEntityWrite_1_1)

        apiKeyTemplatesUsersDb
          .deleteMany(entitiesToDelete)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyTemplatesUsersNotFoundError(entitiesToDelete)))
      }

      "make no changes to the DB" in {
        val entitiesToDelete = List(apiKeyTemplatesUsersEntityWrite_1_1)
        val result = (for {
          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(apiKeyTemplatesUsersEntityWrite_1_1)

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(apiKeyTemplatesUsersEntityWrite_1_1)

          _ <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }
    }

    "there is a row in the DB, but for a different Tenant" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = preExistingEntities.map(_.copy(tenantId = tenantDbId_2))

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete)).transact(transactor)

        result.asserting { case (res, entitiesToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(entitiesToDelete))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = preExistingEntities.map(_.copy(tenantId = tenantDbId_2))

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

    "there is a row in the DB with provided apiKeyTemplateId, but different userId" should {

      "return Left containing ApiKeyTemplatesUsersNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            apiKeyTemplatesUsersEntityWrite_1_2
          )

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            apiKeyTemplatesUsersEntityWrite_1_2
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(apiKeyTemplatesUsersEntityWrite_2_1)

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesUsersEntityWrite_1_1)
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(apiKeyTemplatesUsersEntityWrite_2_1)

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
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_2_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_3_2
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
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_2_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_3_2
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_2_3,
            apiKeyTemplatesUsersEntityWrite_3_2
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            apiKeyTemplatesUsersEntityWrite_1_3
          )

          res <- apiKeyTemplatesUsersDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.last)).transact(transactor)

        result.asserting { case (res, incorrectEntityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesUsersNotFoundError(List(incorrectEntityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1,
            apiKeyTemplatesUsersEntityWrite_1_2,
            apiKeyTemplatesUsersEntityWrite_2_1,
            apiKeyTemplatesUsersEntityWrite_2_3,
            apiKeyTemplatesUsersEntityWrite_3_2
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            apiKeyTemplatesUsersEntityWrite_1_3
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
