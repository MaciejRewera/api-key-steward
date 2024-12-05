package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.resourceServerEntityWrite_1
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsNotFoundError
import apikeysteward.repositories.TestDataInsertions.{PermissionDbId, ResourceServerDbId, TemplateDbId, TenantDbId}
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import cats.data.NonEmptyList
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
      sql"TRUNCATE tenant, resource_server, permission, api_key_template, api_key_templates_permissions CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val resourceServerDb = new ResourceServerDb
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
      : ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[PermissionDbId])] =
    TestDataInsertions
      .insertPrerequisiteTemplatesAndPermissions(tenantDb, resourceServerDb, permissionDb, apiKeyTemplateDb)

  private def convertEntitiesWriteToRead(
      entitiesWrite: List[ApiKeyTemplatesPermissionsEntity.Write]
  ): List[ApiKeyTemplatesPermissionsEntity.Read] =
    entitiesWrite.map { entityWrite =>
      ApiKeyTemplatesPermissionsEntity.Read(
        tenantId = entityWrite.tenantId,
        apiKeyTemplateId = entityWrite.apiKeyTemplateId,
        permissionId = entityWrite.permissionId
      )
    }

  "ApiKeyTemplatesPermissionsDb on insertMany" when {

    "provided with an empty List" should {

      "return Right containing empty List" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesPermissionsDb.insertMany(List.empty)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(List.empty))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesPermissionsDb.insertMany(List.empty)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val entitiesToInsert = List(apiKeyTemplatesPermissionsEntityWrite_1_1)

        apiKeyTemplatesPermissionsDb
          .insertMany(entitiesToInsert)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val entitiesToInsert = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
        val result = for {
          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)

          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToInsert = List(apiKeyTemplatesPermissionsEntityWrite_1_1.copy(tenantId = tenantDbId_2))

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          entitiesToInsert = List(apiKeyTemplatesPermissionsEntityWrite_1_1.copy(tenantId = tenantDbId_2))

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
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
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_2_1
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
          _ <- insertPrerequisiteData()

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_2_1
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided apiKeyTemplateId, but different permissionId" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided apiKeyTemplateId and permissionId but different tenantId" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with a different apiKeyTemplateId, but provided permissionId" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_1
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_1
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided both apiKeyTemplateId and permissionId" should {

      "return Left containing ApiKeyTemplatesPermissionsAlreadyExistsError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_1
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, templateDbId_1, permissionDbId_1)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_1
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (res, preExistingEntities) =>
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          res shouldBe expectedEntities
        }
      }
    }

    "there is ApiKeyTemplate but NO Permissions in the DB" should {

      "return Left containing ReferencedPermissionDoesNotExistError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ReferencedPermissionDoesNotExistError.fromDbId(permissionDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_1
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError.fromDbId(templateDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)
          _ <- permissionDb.insert(permissionEntityWrite_1).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_1
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )

          res <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, templateDbId_1, permissionDbId_1)).transact(transactor)

        result.asserting { case (res, templateId, permissionId) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsAlreadyExistsError(templateId, permissionId))
        }
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )

          _ <- apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (res, preExistingEntities) =>
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          res shouldBe expectedEntities
        }
      }
    }
  }

  "ApiKeyTemplatesPermissionsDb on deleteAllForPermission" when {

    "there is no Tenant in the DB" should {

      "return zero" in {
        apiKeyTemplatesPermissionsDb
          .deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
          .transact(transactor)
          .asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different publicTenantId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_2, publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB, but for a different Permission" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_2)
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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given Permission" should {

      "return the number of deleted rows" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_1
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
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

    "there is no Tenant in the DB" should {

      "return zero" in {
        apiKeyTemplatesPermissionsDb
          .deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          .transact(transactor)
          .asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different publicTenantId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_2, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_2, publicTemplateId_1)
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

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_2)
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

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given ApiKeyTemplate" should {

      "return the number of deleted rows" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3,
            apiKeyTemplatesPermissionsEntityWrite_2_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesPermissionsEntityWrite_2_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeyTemplatesPermissionsDb.deleteAllForApiKeyTemplate(publicTenantId_1, publicTemplateId_1)
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

    "provided with an empty List" should {

      "return Right containing empty List" in {
        apiKeyTemplatesPermissionsDb.deleteMany(List.empty).transact(transactor).asserting(_ shouldBe Right(List.empty))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List.empty

          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
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

    "there is no Tenant in the DB" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_1_1)

        apiKeyTemplatesPermissionsDb
          .deleteMany(entitiesToDelete)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(entitiesToDelete)))
      }

      "make no changes to the DB" in {
        val entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
        val result = (for {
          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_1_1)

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_1_1)

          _ <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different Tenant" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = preExistingEntities.map(_.copy(tenantId = tenantDbId_2))

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete)).transact(transactor)

        result.asserting { case (res, entitiesToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(entitiesToDelete))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = preExistingEntities.map(_.copy(tenantId = tenantDbId_2))

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

    "there is a row in the DB with provided apiKeyTemplateId, but different permissionId" should {

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_1_2)

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_1_2)

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
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_2_1)

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.head)).transact(transactor)

        result.asserting { case (res, entityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(entityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeyTemplatesPermissionsEntityWrite_1_1)
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(apiKeyTemplatesPermissionsEntityWrite_2_1)

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
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_2_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
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
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_2_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
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

      "return Left containing ApiKeyTemplatesPermissionsNotFoundError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_2_3,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            apiKeyTemplatesPermissionsEntityWrite_1_3
          )

          res <- apiKeyTemplatesPermissionsDb.deleteMany(entitiesToDelete)
        } yield (res, entitiesToDelete.last)).transact(transactor)

        result.asserting { case (res, incorrectEntityToDelete) =>
          res shouldBe Left(ApiKeyTemplatesPermissionsNotFoundError(List(incorrectEntityToDelete)))
        }
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_2_3,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToDelete = List(
            preExistingEntities.head,
            preExistingEntities(2),
            preExistingEntities(3),
            apiKeyTemplatesPermissionsEntityWrite_1_3
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

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        val entitiesToFetch = NonEmptyList.of(
          apiKeyTemplatesPermissionsEntityWrite_1_1,
          apiKeyTemplatesPermissionsEntityWrite_1_3,
          apiKeyTemplatesPermissionsEntityWrite_2_1
        )

        apiKeyTemplatesPermissionsDb
          .getAllThatExistFrom(entitiesToFetch)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are no rows in the DB" should {
      "return empty Stream" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToFetch = NonEmptyList.of(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_2_1
          )

          res <- apiKeyTemplatesPermissionsDb.getAllThatExistFrom(entitiesToFetch).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are rows in the DB, but for a different Tenant" should {
      "return empty Stream" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_2_2
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToFetch = NonEmptyList.fromListUnsafe(preExistingEntities.map(_.copy(tenantId = tenantDbId_2)))

          res <- apiKeyTemplatesPermissionsDb.getAllThatExistFrom(entitiesToFetch).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are rows in the DB with different sets of apiKeyTemplateId and permissionId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_2_2
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToFetch = NonEmptyList.of(
            apiKeyTemplatesPermissionsEntityWrite_1_2,
            apiKeyTemplatesPermissionsEntityWrite_2_3,
            apiKeyTemplatesPermissionsEntityWrite_3_1,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )

          res <- apiKeyTemplatesPermissionsDb.getAllThatExistFrom(entitiesToFetch).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }
    }

    "there are rows in the DB with provided sets of apiKeyTemplateId and permissionId, but some are missing" should {
      "return Stream containing matching entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesExpectedToBePresent = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1,
            apiKeyTemplatesPermissionsEntityWrite_2_2,
            apiKeyTemplatesPermissionsEntityWrite_3_2
          )

          preExistingEntities = entitiesExpectedToBePresent ++ List(
            apiKeyTemplatesPermissionsEntityWrite_2_1,
            apiKeyTemplatesPermissionsEntityWrite_3_3
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          entitiesToFetch = entitiesExpectedToBePresent ++ List(
            apiKeyTemplatesPermissionsEntityWrite_2_3,
            apiKeyTemplatesPermissionsEntityWrite_3_1
          )

          res <- apiKeyTemplatesPermissionsDb
            .getAllThatExistFrom(NonEmptyList.fromListUnsafe(entitiesToFetch))
            .compile
            .toList
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
