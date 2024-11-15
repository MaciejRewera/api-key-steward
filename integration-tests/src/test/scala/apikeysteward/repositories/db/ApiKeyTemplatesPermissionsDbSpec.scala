package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApplicationsTestData.applicationEntityWrite_1
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantEntityWrite_1
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsNotFoundError
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import apikeysteward.repositories.TestDataInsertions.{ApplicationDbId, PermissionDbId, TemplateDbId, TenantDbId}
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyTemplatesPermissionsDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <-
      sql"TRUNCATE tenant, application, permission, api_key_template, api_key_templates_permissions CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val applicationDb = new ApplicationDb
  private val permissionDb = new PermissionDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb

  private val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllAssociations: ConnectionIO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
      sql"SELECT * FROM api_key_templates_permissions"
        .query[ApiKeyTemplatesPermissionsEntity.Read]
        .stream
        .compile
        .toList
  }

  private def insertPrerequisiteData()
      : ConnectionIO[(TenantDbId, ApplicationDbId, List[TemplateDbId], List[PermissionDbId])] =
    TestDataInsertions.insertPrerequisiteData(tenantDb, applicationDb, permissionDb, apiKeyTemplateDb)

  private def convertEntitiesWriteToRead(
      entitiesWrite: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): List[ApiKeyTemplatesPermissionsEntity.Read] =
    entitiesWrite.map { entityWrite =>
      ApiKeyTemplatesPermissionsEntity.Read(
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        permissionId = entityWrite.permissionId
      )
    }

  "ApiKeyTemplatesPermissionsDb on insertMany" when {

    "there are no rows in the DB" should {

      "return inserted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (allEntities, entitiesToInsert) =>
          allEntities.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided apiKeyTemplateId, but different permissionId" should {

      "return inserted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2))
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2))
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (allEntities, entitiesToInsert) =>
          allEntities.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with a different apiKeyTemplateId, but provided permissionId" should {

      "return inserted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds.head)
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (allEntities, entitiesToInsert) =>
          allEntities.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided both apiKeyTemplateId and permissionId" should {

      "return Left containing ApiKeyTemplatesPermissionsAlreadyExistsError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, templateIds.head, permissionIds.head)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          dataIds <- insertPrerequisiteData().transact(transactor)
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is ApiKeyTemplate but NO Permission with provided IDs in the DB" should {

      "return Left containing ReferencedPermissionDoesNotExistError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb
            .insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = 101L),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = 102L),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = 103L)
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ReferencedPermissionDoesNotExistError(101L)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          templateId <- apiKeyTemplateDb
            .insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = 101L),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = 102L),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = 103L)
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is Permission in the DB but NO ApiKeyTemplate with provided IDs in the DB" should {

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb
            .insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
          permissionId <- permissionDb
            .insert(permissionEntityWrite_1.copy(applicationId = applicationId))
            .map(_.value.id)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 101L, permissionId = permissionId),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 102L, permissionId = permissionId),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 103L, permissionId = permissionId)
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(101L)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          applicationId <- applicationDb
            .insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)
          permissionId <- permissionDb
            .insert(permissionEntityWrite_1.copy(applicationId = applicationId))
            .map(_.value.id)
            .transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 101L, permissionId = permissionId),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 102L, permissionId = permissionId),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 103L, permissionId = permissionId)
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is an exception returned for one of the subsequent entities" should {

      "return Left containing appropriate ApiKeyTemplatesPermissionsInsertionError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2))
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, templateIds.head, permissionIds.head)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          dataIds <- insertPrerequisiteData().transact(transactor)
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApiKeyTemplatesPermissionsDb on deleteAllForPermission" when {

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different Permission" should {

      "return zero" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_2)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB for given Permission" should {

      "return one" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given Permission" should {

      "return the number of deleted rows" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds.head)
          )
          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicPermissionId_1)
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

  "ApiKeyTemplatesPermissionsDb on deleteAllForApiKeyTemplate" when {

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different Permission" should {

      "return zero" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_2)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB for given Permission" should {

      "return one" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given Permission" should {

      "return the number of deleted rows" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2))
          )
          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTemplateId_1)
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

  "ApiKeyTemplatesPermissionsDb on deleteMany" when {

    "there are no rows in the DB" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )

          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is a row in the DB with provided apiKeyTemplateId, but different permissionId" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1))
          )

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1))
          )

          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with a different apiKeyTemplateId, but provided permissionId" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
          )

          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are rows in the DB with provided both apiKeyTemplateId and permissionId" should {

      "return Right containing deleted entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(2))
          )
          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete)).transact(transactor)

        result.asserting { case (res, entitiesToDelete) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToDelete)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "delete these rows from the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToDelete = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(2))
          )
          entitiesExpectedNotToBeDeleted = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
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

      "return Left containing appropriate ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(2)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2))
          )

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.last)).transact(transactor)

        result.asserting { case (res, incorrectEntityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(incorrectEntityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(2)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(2))
          )

          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
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

  "ApiKeyTemplatesPermissionsDb on getAllThatExistFrom" when {

    "there are no rows in the DB" should {
      "return empty Stream" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesToFetch = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.getAllThatExistFrom(entitiesToFetch).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are rows in the DB with different sets of apiKeyTemplateId and permissionId" should {
      "return empty Stream" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToFetch = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(2)),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )

          res <- apiKeyTemplatesPermissionsDb.getAllThatExistFrom(entitiesToFetch).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are rows in the DB with provided sets of apiKeyTemplateId and permissionId, but some are missing" should {
      "return Stream containing matching entities" in {
        val result = (for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          entitiesExpectedToBePresent = List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1)),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(1))
          )

          preExistingEntities = entitiesExpectedToBePresent ++ List(
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds(2))
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToFetch = entitiesExpectedToBePresent ++ List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(2)),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateIds(2), permissionId = permissionIds.head)
          )

          res <- apiKeyTemplatesPermissionsDb.getAllThatExistFrom(entitiesToFetch).compile.toList
        } yield (res, entitiesExpectedToBePresent)).transact(transactor)

        result.asserting { case (res, entitiesExpectedToBePresent) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(entitiesExpectedToBePresent)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

}
