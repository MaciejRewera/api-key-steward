package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysPermissionsTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.errors.ApiKeysPermissionsDbError.ApiKeysPermissionsInsertionError._
import apikeysteward.repositories.TestDataInsertions._
import apikeysteward.repositories.db.entity.ApiKeysPermissionsEntity
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeysPermissionsDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <-
      sql"TRUNCATE tenant, tenant_user, resource_server, permission, api_key_template, api_key, api_key_data, api_keys_permissions CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val resourceServerDb = new ResourceServerDb
  private val permissionDb = new PermissionDb
  private val userDb = new UserDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val apiKeyDb = new ApiKeyDb
  private val apiKeyDataDb = new ApiKeyDataDb

  private val apiKeysPermissionsDb = new ApiKeysPermissionsDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllAssociations: ConnectionIO[List[ApiKeysPermissionsEntity.Read]] =
      sql"SELECT * FROM api_keys_permissions"
        .query[ApiKeysPermissionsEntity.Read]
        .stream
        .compile
        .toList

    val deleteAllPermissions: ConnectionIO[Int] =
      sql"DELETE FROM permission".update.run

    val deleteAllApiKeys: ConnectionIO[Int] = {
      for {
        _ <- sql"DELETE FROM api_key_data".update.run
        res <- sql"DELETE FROM api_key".update.run
      } yield res
    }
  }

  private def insertPrerequisiteData()
      : ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[UserDbId], List[PermissionDbId])] =
    TestDataInsertions
      .insertPrerequisiteTemplatesAndUsersAndPermissionsAndApiKeys(
        tenantDb,
        userDb,
        resourceServerDb,
        permissionDb,
        apiKeyTemplateDb,
        apiKeyDb,
        apiKeyDataDb
      )

  private def convertEntitiesWriteToRead(
      entitiesWrite: List[ApiKeysPermissionsEntity.Write]
  ): List[ApiKeysPermissionsEntity.Read] =
    entitiesWrite.map { entityWrite =>
      ApiKeysPermissionsEntity.Read(
        tenantId = entityWrite.tenantId,
        apiKeyDataId = entityWrite.apiKeyDataId,
        permissionId = entityWrite.permissionId
      )
    }

  "ApiKeysPermissionsDb on insertMany" when {

    "provided with an empty List" should {

      "return Right containing empty List" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeysPermissionsDb.insertMany(List.empty)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(List.empty))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeysPermissionsDb.insertMany(List.empty)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val entitiesToInsert = List(apiKeysPermissionsEntityWrite_1_1)

        apiKeysPermissionsDb
          .insertMany(entitiesToInsert)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val entitiesToInsert = List(apiKeysPermissionsEntityWrite_1_1)
        val result = for {
          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert).transact(transactor)

          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToInsert = List(apiKeysPermissionsEntityWrite_1_1.copy(tenantId = tenantDbId_2))

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          entitiesToInsert = List(apiKeysPermissionsEntityWrite_1_1.copy(tenantId = tenantDbId_2))

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
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
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_2_1
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
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
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_2_1
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided apiKeyDataId, but different permissionId" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided apiKeyDataId and permissionId but different tenantId" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with a different apiKeyDataId, but provided permissionId" should {

      "return inserted entities" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_3_1
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield (res, entitiesToInsert)).transact(transactor)

        result.asserting { case (res, entitiesToInsert) =>
          val expectedEntities = convertEntitiesWriteToRead(entitiesToInsert)
          res.value should contain theSameElementsAs expectedEntities
        }
      }

      "insert entities into DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_3_1
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities ++ entitiesToInsert)).transact(transactor)

        result.asserting { case (res, allEntities) =>
          res.size shouldBe 3
          val expectedEntities = convertEntitiesWriteToRead(allEntities)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB with provided both apiKeyDataId and permissionId" should {

      "return Left containing ApiKeysPermissionsAlreadyExistsError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_1
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeysPermissionsAlreadyExistsError(apiKeyDataDbId_1, permissionDbId_1)))
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_1
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (res, preExistingEntities) =>
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          res shouldBe expectedEntities
        }
      }
    }

    "there is ApiKeyData but NO Permissions in the DB" should {

      "return Left containing ReferencedPermissionDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- Queries.deleteAllPermissions

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ReferencedPermissionDoesNotExistError.fromDbId(permissionDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- Queries.deleteAllPermissions.transact(transactor)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }
    }

    "there is Permission in the DB but NO ApiKeyData with provided IDs in the DB" should {

      "return Left containing ReferencedApiKeyDoesNotExistError" in {
        val result = (for {
          _ <- insertPrerequisiteData()
          _ <- Queries.deleteAllApiKeys

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_3_1
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ReferencedApiKeyDoesNotExistError.fromDbId(apiKeyDataDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)
          _ <- Queries.deleteAllApiKeys.transact(transactor)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_3_1
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }
    }

    "there is an exception returned for one of the subsequent entities" should {

      "return Left containing appropriate ApiKeysPermissionsInsertionError" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_3
          )

          res <- apiKeysPermissionsDb.insertMany(entitiesToInsert)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeysPermissionsAlreadyExistsError(apiKeyDataDbId_1, permissionDbId_1)))
      }

      "NOT insert any new entity into the DB" in {
        val result = for {
          _ <- insertPrerequisiteData().transact(transactor)

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities).transact(transactor)

          entitiesToInsert = List(
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_3
          )

          _ <- apiKeysPermissionsDb.insertMany(entitiesToInsert).transact(transactor)
          res <- Queries.getAllAssociations.transact(transactor)
        } yield (res, preExistingEntities)

        result.asserting { case (res, preExistingEntities) =>
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          res shouldBe expectedEntities
        }
      }
    }
  }

  "ApiKeysPermissionsDb on deleteAllForPermission" when {

    "there is no Tenant in the DB" should {

      "return zero" in {
        apiKeysPermissionsDb
          .deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
          .transact(transactor)
          .asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different publicTenantId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_2, publicPermissionId_1)
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

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_2)
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

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given Permission" should {

      "return the number of deleted rows" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_3_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_3_2
          )
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_2_1,
            apiKeysPermissionsEntityWrite_3_1
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_3_2
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForPermission(publicTenantId_1, publicPermissionId_1)
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

  "ApiKeysPermissionsDb on deleteAllForApiKey" when {

    "there is no Tenant in the DB" should {

      "return zero" in {
        apiKeysPermissionsDb
          .deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
          .transact(transactor)
          .asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)

          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty)
      }
    }

    "there are no rows in the DB" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          res <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          _ <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }
    }

    "there is a row in the DB, but for a different publicTenantId" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_2, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_2, publicKeyId_1)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB, but for a different ApiKeyData" should {

      "return zero" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 0)
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_2)
          res <- Queries.getAllAssociations
        } yield (res, preExistingEntities)).transact(transactor)

        result.asserting { case (allEntities, preExistingEntities) =>
          allEntities.size shouldBe 1
          val expectedEntities = convertEntitiesWriteToRead(preExistingEntities)
          allEntities should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there is a row in the DB for given ApiKeyData" should {

      "return one" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 1)
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(apiKeysPermissionsEntityWrite_1_1)
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
          res <- Queries.getAllAssociations
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }
    }

    "there are several rows in the DB, some of which are for given ApiKeyTemplate" should {

      "return the number of deleted rows" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          preExistingEntities = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3,
            apiKeysPermissionsEntityWrite_2_2,
            apiKeysPermissionsEntityWrite_3_2
          )
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          res <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe 3)
      }

      "delete these rows from the DB" in {
        val result = (for {
          _ <- insertPrerequisiteData()

          entitiesToDelete = List(
            apiKeysPermissionsEntityWrite_1_1,
            apiKeysPermissionsEntityWrite_1_2,
            apiKeysPermissionsEntityWrite_1_3
          )
          entitiesExpectedNotToBeDeleted = List(
            apiKeysPermissionsEntityWrite_2_2,
            apiKeysPermissionsEntityWrite_3_2
          )

          preExistingEntities = entitiesToDelete ++ entitiesExpectedNotToBeDeleted
          _ <- apiKeysPermissionsDb.insertMany(preExistingEntities)

          _ <- apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId_1, publicKeyId_1)
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

}
