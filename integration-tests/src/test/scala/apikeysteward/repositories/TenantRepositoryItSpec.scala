package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApplicationsTestData.{applicationEntityRead_1, publicApplicationId_1}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.repositories.TestDataInsertions._
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class TenantRepositoryItSpec
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
  private val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val userDb = new UserDb

  private val permissionRepository =
    new PermissionRepository(applicationDb, permissionDb, apiKeyTemplatesPermissionsDb)(transactor)

  private val applicationRepository =
    new ApplicationRepository(tenantDb, applicationDb, permissionDb, permissionRepository)(transactor)

  private val apiKeyTemplateRepository =
    new ApiKeyTemplateRepository(tenantDb, apiKeyTemplateDb, permissionDb, apiKeyTemplatesPermissionsDb)(transactor)

  private val userRepository = new UserRepository(tenantDb, userDb)(transactor)

  private val repository =
    new TenantRepository(tenantDb, applicationRepository, apiKeyTemplateRepository, userRepository)(transactor)

  private object Queries extends DoobieCustomMeta {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllTenants: ConnectionIO[List[TenantEntity.Read]] =
      sql"SELECT * FROM tenant".query[TenantEntity.Read].stream.compile.toList

    val getAllUsers: ConnectionIO[List[UserEntity.Read]] =
      sql"SELECT * FROM tenant_user".query[UserEntity.Read].stream.compile.toList

    val getAllApplications: ConnectionIO[List[ApplicationEntity.Read]] =
      sql"SELECT * FROM application".query[ApplicationEntity.Read].stream.compile.toList

    val getAllPermissions: ConnectionIO[List[PermissionEntity.Read]] =
      sql"SELECT * FROM permission".query[PermissionEntity.Read].stream.compile.toList

    val getAllApiKeyTemplates: doobie.ConnectionIO[List[ApiKeyTemplateEntity.Read]] =
      sql"SELECT * FROM api_key_template".query[ApiKeyTemplateEntity.Read].stream.compile.toList

    val getAllAssociations: ConnectionIO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
      sql"SELECT * FROM api_key_templates_permissions"
        .query[ApiKeyTemplatesPermissionsEntity.Read]
        .stream
        .compile
        .toList
  }

  private type UserDbId = Long

  private def insertPrerequisiteData()
      : IO[(TenantDbId, ApplicationDbId, List[TemplateDbId], List[PermissionDbId], List[UserDbId])] =
    (for {
      dataIds <- TestDataInsertions.insertPrerequisiteData(
        tenantDb,
        applicationDb,
        permissionDb,
        apiKeyTemplateDb
      )
      (tenantId, applicationId, templateIds, permissionIds) = dataIds

      associationEntities = List(
        ApiKeyTemplatesPermissionsEntity
          .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
      )
      _ <- apiKeyTemplatesPermissionsDb.insertMany(associationEntities)

      userId_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
      userId_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
      userId_3 <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId)).map(_.value.id)

      userIds = List(userId_1, userId_2, userId_3)
    } yield (tenantId, applicationId, templateIds, permissionIds, userIds)).transact(transactor)

  "TenantRepository on delete" when {

    "given Tenant is active" should {

      "NOT delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds, _) = dataIds

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllAssociations.transact(transactor)

          expectedEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Read(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, applicationId, _, _, _) = dataIds

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield (res, applicationId)

        result.asserting { case (res, applicationId) =>
          val expectedEntities = List(
            permissionEntityRead_1.copy(id = res.head.id, applicationId = applicationId),
            permissionEntityRead_2.copy(id = res(1).id, applicationId = applicationId),
            permissionEntityRead_3.copy(id = res(2).id, applicationId = applicationId)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Applications" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, applicationId, _, _, _) = dataIds

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApplications.transact(transactor)
        } yield (res, tenantId, applicationId)

        result.asserting { case (res, tenantId, applicationId) =>
          val expectedEntities = List(
            applicationEntityRead_1.copy(id = applicationId, tenantId = tenantId, deactivatedAt = Some(nowInstant))
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, templateIds, _, _) = dataIds

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield (res, tenantId, templateIds)

        result.asserting { case (res, tenantId, templateIds) =>
          val expectedEntities = List(
            apiKeyTemplateEntityRead_1.copy(tenantId = tenantId, id = templateIds.head),
            apiKeyTemplateEntityRead_2.copy(tenantId = tenantId, id = templateIds(1)),
            apiKeyTemplateEntityRead_3.copy(tenantId = tenantId, id = templateIds(2))
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Users" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, _, _, userIds) = dataIds

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield (res, tenantId, userIds)

        result.asserting { case (res, tenantId, userIds) =>
          val expectedEntities = List(
            userEntityRead_1.copy(tenantId = tenantId, id = userIds.head),
            userEntityRead_2.copy(tenantId = tenantId, id = userIds(1)),
            userEntityRead_3.copy(tenantId = tenantId, id = userIds(2))
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this Tenant" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, _, _, _) = dataIds

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield (res, tenantId)

        result.asserting { case (res, tenantId) =>
          val expectedEntities = List(tenantEntityRead_1.copy(id = tenantId))

          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "at least one of related Applications is active" should {

      "NOT delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds, _) = dataIds

          _ <- applicationRepository.activate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllAssociations.transact(transactor)

          expectedEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Read(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, applicationId, _, _, _) = dataIds

          _ <- applicationRepository.activate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield (res, applicationId)

        result.asserting { case (res, applicationId) =>
          val expectedEntities = List(
            permissionEntityRead_1.copy(id = res.head.id, applicationId = applicationId),
            permissionEntityRead_2.copy(id = res(1).id, applicationId = applicationId),
            permissionEntityRead_3.copy(id = res(2).id, applicationId = applicationId)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Applications" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, applicationId, _, _, _) = dataIds

          _ <- applicationRepository.activate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApplications.transact(transactor)
        } yield (res, tenantId, applicationId)

        result.asserting { case (res, tenantId, applicationId) =>
          val expectedEntities = List(applicationEntityRead_1.copy(id = applicationId, tenantId = tenantId))

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, templateIds, _, _) = dataIds

          _ <- applicationRepository.activate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield (res, tenantId, templateIds)

        result.asserting { case (res, tenantId, templateIds) =>
          val expectedEntities = List(
            apiKeyTemplateEntityRead_1.copy(tenantId = tenantId, id = templateIds.head),
            apiKeyTemplateEntityRead_2.copy(tenantId = tenantId, id = templateIds(1)),
            apiKeyTemplateEntityRead_3.copy(tenantId = tenantId, id = templateIds(2))
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Users" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, _, _, userIds) = dataIds

          _ <- applicationRepository.activate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield (res, tenantId, userIds)

        result.asserting { case (res, tenantId, userIds) =>
          val expectedEntities = List(
            userEntityRead_1.copy(tenantId = tenantId, id = userIds.head),
            userEntityRead_2.copy(tenantId = tenantId, id = userIds(1)),
            userEntityRead_3.copy(tenantId = tenantId, id = userIds(2))
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this Tenant" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, _, _, _) = dataIds

          _ <- applicationRepository.activate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield (res, tenantId)

        result.asserting { case (res, tenantId) =>
          val expectedEntities = List(tenantEntityRead_1.copy(id = tenantId, deactivatedAt = Some(nowInstant)))

          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are entities in the DB, related to given inactive Tenant" should {

      "delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "delete Applications" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApplications.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }

      "delete ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }

      "delete Users" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }

      "delete the Tenant" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- applicationRepository.deactivate(publicApplicationId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }
  }

}
