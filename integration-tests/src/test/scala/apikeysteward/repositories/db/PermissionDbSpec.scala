package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData._
import apikeysteward.base.testdata.TenantsTestData.{
  publicTenantId_1,
  publicTenantId_2,
  tenantDbId_1,
  tenantEntityWrite_1
}
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionNotFoundError
import apikeysteward.repositories.TestDataInsertions._
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesPermissionsEntity, PermissionEntity, ResourceServerEntity}
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class PermissionDbSpec
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
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb

  private val permissionDb = new PermissionDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllPermissions: doobie.ConnectionIO[List[PermissionEntity.Read]] =
      sql"SELECT * FROM permission".query[PermissionEntity.Read].stream.compile.toList

    val getAllResourceServers: doobie.ConnectionIO[List[ResourceServerEntity.Read]] =
      sql"SELECT * FROM resource_server".query[ResourceServerEntity.Read].stream.compile.toList
  }

  "PermissionDb on insert" when {

    "there is no ResourceServer with provided resourceServerId in the DB" should {

      "return Left containing ReferencedResourceServerDoesNotExistError" in {
        permissionDb
          .insert(permissionEntityWrite_1)
          .transact(transactor)
          .asserting(
            _ shouldBe Left(
              ReferencedResourceServerDoesNotExistError.fromDbId(permissionEntityWrite_1.resourceServerId)
            )
          )
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- permissionDb.insert(permissionEntityWrite_1).transact(transactor)
          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- permissionDb.insert(permissionEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(permissionEntityRead_1))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- permissionDb.insert(permissionEntityWrite_1)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(permissionEntityRead_1))
      }
    }

    "there is a row in the DB with a different publicPermissionId" when {

      "the row has the same name, but different resourceServerId" should {

        val firstEntity = permissionEntityWrite_1
        val secondEntity =
          permissionEntityWrite_2.copy(resourceServerId = resourceServerDbId_2, name = firstEntity.name)
        val expectedSecondEntity =
          permissionEntityRead_2.copy(resourceServerId = resourceServerDbId_2, name = firstEntity.name)

        "return inserted entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
            _ <- permissionDb.insert(firstEntity)

            res <- permissionDb.insert(secondEntity)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe Right(expectedSecondEntity))
        }

        "insert entity into DB" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
            _ <- permissionDb.insert(firstEntity)

            _ <- permissionDb.insert(secondEntity)
            res <- Queries.getAllPermissions
          } yield res).transact(transactor)

          result.asserting { allPermissions =>
            allPermissions.size shouldBe 2

            val expectedPermissions = Seq(
              permissionEntityRead_1,
              permissionEntityRead_2.copy(resourceServerId = resourceServerDbId_2, name = firstEntity.name)
            )
            allPermissions should contain theSameElementsAs expectedPermissions
          }
        }
      }

      "the row has the same resourceServerId, but different name" should {

        val firstEntity = permissionEntityWrite_1
        val secondEntity = permissionEntityWrite_2.copy(
          tenantId = tenantDbId_1,
          resourceServerId = resourceServerDbId_1
        )
        val expectedSecondEntity = permissionEntityRead_2.copy(
          tenantId = tenantDbId_1,
          resourceServerId = resourceServerDbId_1
        )

        "return inserted entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(firstEntity)

            res <- permissionDb.insert(secondEntity)
          } yield res).transact(transactor)

          result.asserting(_ shouldBe Right(expectedSecondEntity))
        }

        "insert entity into DB" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(firstEntity)

            _ <- permissionDb.insert(secondEntity)
            res <- Queries.getAllPermissions
          } yield res).transact(transactor)

          result.asserting { allPermissions =>
            allPermissions.size shouldBe 2

            val expectedPermissions = Seq(
              permissionEntityRead_1,
              permissionEntityRead_2.copy(tenantId = tenantDbId_1, resourceServerId = resourceServerDbId_1)
            )
            allPermissions should contain theSameElementsAs expectedPermissions
          }
        }
      }

      "the row has the same both name and resourceServerId" should {

        val firstEntity = permissionEntityWrite_1
        val secondEntity = permissionEntityWrite_2.copy(
          resourceServerId = resourceServerDbId_1,
          name = permissionEntityWrite_1.name
        )

        "return Left containing PermissionAlreadyExistsForThisResourceServerError" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(firstEntity)

            res <- permissionDb.insert(secondEntity)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Left(PermissionAlreadyExistsForThisResourceServerError(permissionName_1, resourceServerDbId_1))
            res.left.value.message shouldBe s"Permission with name = $permissionName_1 already exists for ResourceServer with ID = [$resourceServerDbId_1]."
          }
        }

        "NOT insert the second entity into DB" in {
          val result = for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)
            _ <- permissionDb.insert(firstEntity).transact(transactor)

            _ <- permissionDb.insert(secondEntity).transact(transactor)
            res <- Queries.getAllPermissions.transact(transactor)
          } yield res

          result.asserting(_ shouldBe List(permissionEntityRead_1))
        }
      }
    }

    "there is a row in the DB with the same publicPermissionId" should {

      val firstEntity = permissionEntityWrite_1
      val secondEntity = permissionEntityWrite_2.copy(publicPermissionId = firstEntity.publicPermissionId)

      "return Left containing PermissionAlreadyExistsError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          resourceServerId_1 <- resourceServerDb
            .insert(resourceServerEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
          resourceServerId_2 <- resourceServerDb
            .insert(resourceServerEntityWrite_2.copy(tenantId = tenantId))
            .map(_.value.id)

          _ <- permissionDb.insert(firstEntity.copy(resourceServerId = resourceServerId_1))

          res <- permissionDb.insert(secondEntity.copy(resourceServerId = resourceServerId_2))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(PermissionAlreadyExistsError(publicPermissionIdStr_1))
          res.left.value.message shouldBe s"Permission with publicPermissionId = [$publicPermissionIdStr_1] already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          resourceServerId_1 <- resourceServerDb
            .insert(resourceServerEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)
          resourceServerId_2 <- resourceServerDb
            .insert(resourceServerEntityWrite_2.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)

          _ <- permissionDb.insert(firstEntity.copy(resourceServerId = resourceServerId_1)).transact(transactor)

          _ <- permissionDb.insert(secondEntity.copy(resourceServerId = resourceServerId_2)).transact(transactor)
          res <- Queries.getAllPermissions.transact(transactor)
        } yield (res, resourceServerId_1)

        result.asserting { case (allPermissions, expectedResourceServerId) =>
          allPermissions.size shouldBe 1

          val resultPermission = allPermissions.head
          resultPermission shouldBe permissionEntityRead_1.copy(
            id = resultPermission.id,
            resourceServerId = expectedResourceServerId
          )
        }
      }
    }
  }

  "PermissionDb on delete" when {

    "there are no Tenants in the DB" should {

      "return Left containing PermissionNotFoundError" in {
        permissionDb
          .delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- permissionDb
            .delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
            .transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are no Permissions in the DB" should {

      "return Left containing PermissionNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB for a different publicTenantId" should {

      "return Left containing PermissionNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.delete(publicTenantId_2, publicResourceServerId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)
          _ <- permissionDb.insert(permissionEntityWrite_1).transact(transactor)

          _ <- permissionDb
            .delete(publicTenantId_2, publicResourceServerId_1, publicPermissionId_2)
            .transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(resourceServerEntityRead_1))
      }
    }

    "there is a Permission in the DB with a different publicPermissionId" should {

      "return Left containing PermissionNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(PermissionNotFoundError(publicResourceServerId_1, publicPermissionId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          _ <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_2)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting { allPermissions =>
          allPermissions.size shouldBe 1

          val resultPermission = allPermissions.head
          resultPermission shouldBe permissionEntityRead_1.copy(
            id = resultPermission.id,
            resourceServerId = resultPermission.resourceServerId
          )
        }
      }
    }

    "there is a Permission in the DB with a different publicResourceServerId" should {

      "return Left containing PermissionNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.delete(publicTenantId_1, publicResourceServerId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(PermissionNotFoundError(publicResourceServerId_2, publicPermissionId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          _ <- permissionDb.delete(publicTenantId_1, publicResourceServerId_2, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting { allPermissions =>
          allPermissions.size shouldBe 1

          val resultPermission = allPermissions.head
          resultPermission shouldBe permissionEntityRead_1.copy(
            id = resultPermission.id,
            resourceServerId = resultPermission.resourceServerId
          )
        }
      }
    }

    "there is a Permission in the DB with given both publicResourceServerId and publicPermissionId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          entityRead <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        } yield (res, entityRead.value)).transact(transactor)

        result.asserting { case (res, entityRead) =>
          res shouldBe Right(
            permissionEntityRead_1.copy(id = entityRead.id, resourceServerId = entityRead.resourceServerId)
          )
        }
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          _ <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "make NO changes to the resourceServer table" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          resourceServerEntity <- resourceServerDb.insert(resourceServerEntityWrite_1).map(_.value)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          _ <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          res <- Queries.getAllResourceServers
        } yield (res, resourceServerEntity)).transact(transactor)

        result.asserting { case (allResourceServers, resourceServerEntity) =>
          allResourceServers.size shouldBe 1

          allResourceServers shouldBe List(resourceServerEntity)
        }
      }
    }

    "there are several Permissions in the DB but only one with given both publicResourceServerId and publicPermissionId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- permissionDb.insert(permissionEntityWrite_1)
          _ <- permissionDb.insert(
            permissionEntityWrite_2.copy(tenantId = tenantDbId_1, resourceServerId = resourceServerDbId_1)
          )
          _ <- permissionDb.insert(
            permissionEntityWrite_3.copy(tenantId = tenantDbId_1, resourceServerId = resourceServerDbId_2)
          )

          res <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(permissionEntityRead_1))
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          resourceServerId <- resourceServerDb.insert(resourceServerEntityWrite_1).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1)
          entityRead_2 <- permissionDb.insert(
            permissionEntityWrite_2.copy(tenantId = tenantDbId_1, resourceServerId = resourceServerId)
          )
          entityRead_3 <- permissionDb.insert(
            permissionEntityWrite_3.copy(tenantId = tenantDbId_1, resourceServerId = resourceServerId)
          )

          _ <- permissionDb.delete(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allPermissions, entityRead_2, entityRead_3) =>
          allPermissions.size shouldBe 2

          allPermissions should contain theSameElementsAs Seq(entityRead_2, entityRead_3)
        }
      }
    }
  }

  "PermissionDb on getBy" when {

    "there are no Permissions in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- permissionDb.getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB for a different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getBy(publicTenantId_2, publicResourceServerId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB with different publicResourceServerId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getBy(publicTenantId_1, publicResourceServerId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB with different publicPermissionId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB with the same both publicResourceServerId and publicPermissionId" should {
      "return this entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getBy(publicTenantId_1, publicResourceServerId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(permissionEntityRead_1.copy(id = res.get.id, resourceServerId = res.get.resourceServerId))
        }
      }
    }
  }

  "PermissionDb on getByPublicPermissionId" when {

    "there are no Permissions in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- permissionDb.getByPublicPermissionId(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB for a different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getByPublicPermissionId(publicTenantId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB with different publicPermissionId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getByPublicPermissionId(publicTenantId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a Permission in the DB with the same publicPermissionId" should {
      "return this entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- permissionDb.insert(permissionEntityWrite_1)

          res <- permissionDb.getByPublicPermissionId(publicTenantId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(permissionEntityRead_1.copy(id = res.get.id, resourceServerId = res.get.resourceServerId))
        }
      }
    }
  }

  "PermissionDb on getAllForTemplate" when {

    "there are NO ApiKeyTemplates in the DB" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB for a different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          permissionId <- permissionDb.insert(permissionEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = permissionId)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- permissionDb.getAllForTemplate(publicTenantId_2, publicTemplateId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB, but with a different publicTemplateId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          permissionId <- permissionDb.insert(permissionEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = permissionId)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB, but there are no ApiKeyTemplatesPermissions for this Template" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- permissionDb.insert(permissionEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB with a single ApiKeyTemplatesPermissions" should {
      "return this single ApiKeyTemplatesPermissions" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          permissionId <- permissionDb.insert(permissionEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = permissionId)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          expectedPermissionEntities = List(
            permissionEntityRead_1.copy(id = permissionId)
          )

          res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
        } yield (res, expectedPermissionEntities)).transact(transactor)

        result.asserting { case (res, expectedPermissionEntities) =>
          res shouldBe expectedPermissionEntities
        }
      }
    }

    "there is an ApiKeyTemplate in the DB with multiple ApiKeyTemplatesPermissions" should {
      "return all these ApiKeyTemplatesPermissions" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          resourceServerId <- resourceServerDb.insert(resourceServerEntityWrite_1).map(_.value.id)

          permissionId_1 <- permissionDb
            .insert(permissionEntityWrite_1.copy(resourceServerId = resourceServerId))
            .map(_.value.id)
          permissionId_2 <- permissionDb
            .insert(permissionEntityWrite_2.copy(resourceServerId = resourceServerId))
            .map(_.value.id)
          permissionId_3 <- permissionDb
            .insert(permissionEntityWrite_3.copy(resourceServerId = resourceServerId))
            .map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = permissionId_1),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = permissionId_2),
            ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = templateId, permissionId = permissionId_3)
          )
          _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

          expectedPermissionEntities = List(
            permissionEntityRead_1.copy(id = permissionId_1, resourceServerId = resourceServerId),
            permissionEntityRead_2.copy(id = permissionId_2, resourceServerId = resourceServerId),
            permissionEntityRead_3.copy(id = permissionId_3, resourceServerId = resourceServerId)
          )

          res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
        } yield (res, expectedPermissionEntities)).transact(transactor)

        result.asserting { case (res, expectedPermissionEntities) =>
          res.size shouldBe 3
          res should contain theSameElementsAs expectedPermissionEntities
        }
      }
    }

    "there are several ApiKeyTemplates in the DB with associated ApiKeyTemplatesPermissions" when {

      def insertPrerequisiteData()
          : ConnectionIO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[PermissionDbId])] =
        TestDataInsertions.insertPrerequisiteTemplatesAndPermissions(
          tenantDb,
          resourceServerDb,
          permissionDb,
          apiKeyTemplateDb
        )

      "there are NO ApiKeyTemplatesPermissions for given publicTemplateId" should {
        "return empty Stream" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (_, _, templateIds, permissionIds) = dataIds

            preExistingEntities = List(
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1)),
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
            )
            _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

            res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a single ApiKeyTemplatesPermissions for given publicTemplateId" should {
        "return this single ApiKeyTemplatesPermissions" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (_, resourceServerId, templateIds, permissionIds) = dataIds

            preExistingEntityExpectedToBeFetched = List(
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head)
            )

            preExistingEntities = preExistingEntityExpectedToBeFetched ++ List(
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1)),
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
            )
            _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

            expectedPermissionEntities = List(
              permissionEntityRead_1.copy(id = permissionIds.head, resourceServerId = resourceServerId)
            )

            res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
          } yield (res, expectedPermissionEntities)).transact(transactor)

          result.asserting { case (res, expectedPermissionEntities) =>
            res.size shouldBe 1
            res shouldBe expectedPermissionEntities
          }
        }
      }

      "there are several ApiKeyTemplatesPermissions got given publicTemplateId" should {
        "return all these ApiKeyTemplatesPermissions" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (_, resourceServerId, templateIds, permissionIds) = dataIds

            preExistingEntitiesExpectedToBeFetched = List(
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds(1))
            )

            preExistingEntities = preExistingEntitiesExpectedToBeFetched ++ List(
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1)),
              ApiKeyTemplatesPermissionsEntity
                .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head)
            )
            _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)

            expectedPermissionEntities = List(
              permissionEntityRead_1.copy(id = permissionIds.head, resourceServerId = resourceServerId),
              permissionEntityRead_2.copy(id = permissionIds(1), resourceServerId = resourceServerId)
            )

            res <- permissionDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
          } yield (res, expectedPermissionEntities)).transact(transactor)

          result.asserting { case (res, expectedPermissionEntities) =>
            res.size shouldBe 2
            res should contain theSameElementsAs expectedPermissionEntities
          }
        }
      }
    }
  }

  "PermissionDb on getAllBy" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        permissionDb
          .getAllBy(publicTenantId_1, publicTemplateId_1)(None)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "there is a Tenant in the DB, but with a different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- permissionDb.getAllBy(publicTenantId_2, publicTemplateId_1)(None).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }

    "provided with empty nameFragment" when {

      val nameFragment = Option.empty[String]

      "there are no rows in the DB" should {
        "return empty Stream" in {
          permissionDb
            .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment)
            .compile
            .toList
            .transact(transactor)
            .asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB for a different publicTenantId" should {
        "return empty Stream" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(permissionEntityWrite_1)

            res <- permissionDb.getAllBy(publicTenantId_2, publicResourceServerId_1)(nameFragment).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with a different resourceServerId" should {
        "return empty Stream" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(permissionEntityWrite_1)

            res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_2)(nameFragment).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with the same resourceServerId" should {
        "return Stream containing this single entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(permissionEntityWrite_1)

            res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ should contain theSameElementsAs List(permissionEntityRead_1))
        }
      }

      "there are several rows in the DB" should {
        "return Stream containing only entities with the same resourceServerId" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
            _ <- permissionDb.insert(permissionEntityWrite_1)
            _ <- permissionDb.insert(permissionEntityWrite_2.copy(resourceServerId = resourceServerDbId_2))
            _ <- permissionDb.insert(permissionEntityWrite_3)

            res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ should contain theSameElementsAs List(permissionEntityRead_1, permissionEntityRead_3))
        }
      }
    }

    "provided with non-empty nameFragment" when {

      "there are no rows in the DB" should {
        "return empty Stream" in {
          permissionDb
            .getAllBy(publicTenantId_1, publicResourceServerId_1)(Some(permissionName_1))
            .compile
            .toList
            .transact(transactor)
            .asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB for a different publicTenantId" should {
        "return empty Stream" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(permissionEntityWrite_1)

            res <- permissionDb
              .getAllBy(publicTenantId_2, publicResourceServerId_1)(Some(permissionName_1))
              .compile
              .toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with a different resourceServerId but matching name" should {
        "return empty Stream" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- permissionDb.insert(permissionEntityWrite_1)

            res <- permissionDb
              .getAllBy(publicTenantId_1, publicResourceServerId_2)(Some(permissionName_1))
              .compile
              .toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with provided resourceServerId" when {

        "the row has a different name" should {
          "return empty Stream" in {
            val nameFragment = Option("write")

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              _ <- permissionDb.insert(permissionEntityWrite_1)

              res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment).compile.toList
            } yield res).transact(transactor)

            result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
          }
        }

        "the row has name column exactly the same as provided name" should {
          "return Stream containing this entity" in {
            val nameFragment = Option(permissionName_1)

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              entityRead <- permissionDb.insert(permissionEntityWrite_1)

              res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment).compile.toList
            } yield (res, entityRead.value)).transact(transactor)

            result.asserting { case (res, entityRead) =>
              res shouldBe List(entityRead)
            }
          }
        }

        "the row has name column matching characters of provided name" should {
          "return Stream containing this entity" in {
            val nameFragment_1 = Some("read:")
            val nameFragment_2 = Some(":perm")
            val nameFragment_3 = Some("1")

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              entityRead <- permissionDb.insert(permissionEntityWrite_1)

              res_1 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_1)
                .compile
                .toList
              res_2 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_2)
                .compile
                .toList
              res_3 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_3)
                .compile
                .toList
            } yield (res_1, res_2, res_3, entityRead.value)).transact(transactor)

            result.asserting { case (res_1, res_2, res_3, entityRead) =>
              res_1 shouldBe List(entityRead)
              res_2 shouldBe List(entityRead)
              res_3 shouldBe List(entityRead)
            }
          }
        }

        "the row has name column matching characters of provided name, but the capitalisation is different" should {
          "return Stream containing this entity" in {
            val nameFragment_1 = Some("Read:")
            val nameFragment_2 = Some(":PERM")

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              entityRead <- permissionDb.insert(permissionEntityWrite_1)

              res_1 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_1)
                .compile
                .toList
              res_2 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_2)
                .compile
                .toList
            } yield (res_1, res_2, entityRead.value)).transact(transactor)

            result.asserting { case (res_1, res_2, entityRead) =>
              res_1 shouldBe List(entityRead)
              res_2 shouldBe List(entityRead)
            }
          }
        }
      }

      "there are several rows in the DB with provided resourceServerId" when {

        "none of them has matching name" should {
          "return empty Stream" in {
            val nameFragment = Option("write")

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              _ <- permissionDb.insert(permissionEntityWrite_1)
              _ <- permissionDb.insert(permissionEntityWrite_2)

              res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment).compile.toList
            } yield res).transact(transactor)

            result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
          }
        }

        "one of them has name column exactly the same as provided name" should {
          "return Stream containing this entity" in {
            val nameFragment = Some(permissionName_1)

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              entityRead_1 <- permissionDb.insert(permissionEntityWrite_1)
              _ <- permissionDb.insert(permissionEntityWrite_2)
              _ <- permissionDb.insert(permissionEntityWrite_3)

              res <- permissionDb.getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment).compile.toList
            } yield (res, entityRead_1.value)).transact(transactor)

            result.asserting { case (res, entityRead_1) =>
              res shouldBe List(entityRead_1)
            }
          }
        }

        "some of them have name column matching characters of provided name" should {
          "return Stream containing these entities" in {
            val nameFragment_1 = Some("read:")
            val nameFragment_2 = Some(":perm")
            val nameFragment_3 = Some("2")

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              entityRead_1 <- permissionDb.insert(permissionEntityWrite_1)
              entityRead_2 <- permissionDb.insert(permissionEntityWrite_2)
              entityRead_3 <- permissionDb.insert(permissionEntityWrite_3)

              res_1 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_1)
                .compile
                .toList
              res_2 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_2)
                .compile
                .toList
              res_3 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_3)
                .compile
                .toList
            } yield (res_1, res_2, res_3, entityRead_1.value, entityRead_2.value, entityRead_3.value))
              .transact(transactor)

            result.asserting { case (res_1, res_2, res_3, entityRead_1, entityRead_2, entityRead_3) =>
              res_1 should contain theSameElementsAs List(entityRead_1, entityRead_2)
              res_2 should contain theSameElementsAs List(entityRead_1, entityRead_2, entityRead_3)
              res_3 shouldBe List(entityRead_2)
            }
          }
        }

        "some of them have name column matching characters of provided name, but the capitalisation is different" should {
          "return Stream containing these entities" in {
            val nameFragment_1 = Some("Read:")
            val nameFragment_2 = Some(":PERM")
            val nameFragment_3 = Some("RiTe")

            val result = (for {
              _ <- tenantDb.insert(tenantEntityWrite_1)
              _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

              entityRead_1 <- permissionDb.insert(permissionEntityWrite_1)
              entityRead_2 <- permissionDb.insert(permissionEntityWrite_2)
              entityRead_3 <- permissionDb.insert(permissionEntityWrite_3)

              res_1 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_1)
                .compile
                .toList
              res_2 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_2)
                .compile
                .toList
              res_3 <- permissionDb
                .getAllBy(publicTenantId_1, publicResourceServerId_1)(nameFragment_3)
                .compile
                .toList
            } yield (res_1, res_2, res_3, entityRead_1.value, entityRead_2.value, entityRead_3.value))
              .transact(transactor)

            result.asserting { case (res_1, res_2, res_3, entityRead_1, entityRead_2, entityRead_3) =>
              res_1 should contain theSameElementsAs List(entityRead_1, entityRead_2)
              res_2 should contain theSameElementsAs List(entityRead_1, entityRead_2, entityRead_3)
              res_3 shouldBe List(entityRead_3)
            }
          }
        }
      }
    }
  }

}
