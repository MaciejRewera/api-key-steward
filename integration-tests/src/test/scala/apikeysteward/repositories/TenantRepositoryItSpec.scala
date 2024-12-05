package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.repositories.TestDataInsertions._
import apikeysteward.repositories.db.entity._
import apikeysteward.repositories.db._
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class TenantRepositoryItSpec
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

  private val uuidGenerator = new UuidGenerator
  private val tenantDb = new TenantDb
  private val resourceServerDb = new ResourceServerDb
  private val permissionDb = new PermissionDb
  private val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
  private val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val userDb = new UserDb

  private val permissionRepository =
    new PermissionRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, apiKeyTemplatesPermissionsDb)(
      transactor
    )

  private val resourceServerRepository =
    new ResourceServerRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)(
      transactor
    )

  private val apiKeyTemplateRepository =
    new ApiKeyTemplateRepository(
      uuidGenerator,
      tenantDb,
      apiKeyTemplateDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeyTemplatesUsersDb
    )(transactor)

  private val userRepository = new UserRepository(uuidGenerator, tenantDb, userDb, apiKeyTemplatesUsersDb)(transactor)

  private val repository =
    new TenantRepository(uuidGenerator, tenantDb, resourceServerRepository, apiKeyTemplateRepository, userRepository)(
      transactor
    )

  private object Queries extends DoobieCustomMeta {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllTenants: ConnectionIO[List[TenantEntity.Read]] =
      sql"SELECT * FROM tenant".query[TenantEntity.Read].stream.compile.toList

    val getAllUsers: ConnectionIO[List[UserEntity.Read]] =
      sql"SELECT * FROM tenant_user".query[UserEntity.Read].stream.compile.toList

    val getAllResourceServers: ConnectionIO[List[ResourceServerEntity.Read]] =
      sql"SELECT * FROM resource_server".query[ResourceServerEntity.Read].stream.compile.toList

    val getAllPermissions: ConnectionIO[List[PermissionEntity.Read]] =
      sql"SELECT * FROM permission".query[PermissionEntity.Read].stream.compile.toList

    val getAllApiKeyTemplates: doobie.ConnectionIO[List[ApiKeyTemplateEntity.Read]] =
      sql"SELECT * FROM api_key_template".query[ApiKeyTemplateEntity.Read].stream.compile.toList

    val getAllPermissionTemplateAssociations: ConnectionIO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
      sql"SELECT * FROM api_key_templates_permissions"
        .query[ApiKeyTemplatesPermissionsEntity.Read]
        .stream
        .compile
        .toList

    val getAllUserTemplateAssociations: ConnectionIO[List[ApiKeyTemplatesUsersEntity.Read]] =
      sql"SELECT * FROM api_key_templates_users".query[ApiKeyTemplatesUsersEntity.Read].stream.compile.toList

  }

  private def insertPrerequisiteData()
      : IO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[PermissionDbId], List[UserDbId])] =
    (for {
      dataIds <- TestDataInsertions.insertPrerequisiteTemplatesAndPermissions(
        tenantDb,
        resourceServerDb,
        permissionDb,
        apiKeyTemplateDb
      )
      (tenantId, resourceServerId, templateIds, permissionIds) = dataIds

      associationEntities = List(
        ApiKeyTemplatesPermissionsEntity
          .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
      )
      _ <- apiKeyTemplatesPermissionsDb.insertMany(associationEntities)

      userId_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
      userId_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
      userId_3 <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId)).map(_.value.id)
      userIds = List(userId_1, userId_2, userId_3)

      associationEntities = List(
        ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
        ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head),
        ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(1))
      )
      _ <- apiKeyTemplatesUsersDb.insertMany(associationEntities)

    } yield (tenantId, resourceServerId, templateIds, permissionIds, userIds)).transact(transactor)

  "TenantRepository on delete" when {

    "given Tenant is active" should {

      "NOT delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds, _) = dataIds

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionTemplateAssociations.transact(transactor)

          expectedEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Read(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between these Users and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, _, userIds) = dataIds

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUserTemplateAssociations.transact(transactor)

          expectedEntities = List(
            ApiKeyTemplatesUsersEntity.Read(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Read(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Read(apiKeyTemplateId = templateIds(1), userId = userIds(1))
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, resourceServerId, _, _, _) = dataIds

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield (res, resourceServerId)

        result.asserting { case (res, resourceServerId) =>
          val expectedEntities = List(
            permissionEntityRead_1.copy(id = res.head.id, resourceServerId = resourceServerId),
            permissionEntityRead_2.copy(id = res(1).id, resourceServerId = resourceServerId),
            permissionEntityRead_3.copy(id = res(2).id, resourceServerId = resourceServerId)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ResourceServers" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, resourceServerId, _, _, _) = dataIds

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield (res, tenantId, resourceServerId)

        result.asserting { case (res, tenantId, resourceServerId) =>
          val expectedEntities = List(
            resourceServerEntityRead_1.copy(
              id = resourceServerId,
              tenantId = tenantId,
              deactivatedAt = Some(nowInstant)
            )
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, templateIds, _, _) = dataIds

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
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

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            userEntityRead_1.copy(tenantId = tenantDbId_1),
            userEntityRead_2.copy(tenantId = tenantDbId_1),
            userEntityRead_3.copy(tenantId = tenantDbId_1)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this Tenant" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, _, _, _) = dataIds

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
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

    "at least one of related ResourceServers is active" should {

      "NOT delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds, _) = dataIds

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionTemplateAssociations.transact(transactor)

          expectedEntities = List(
            ApiKeyTemplatesPermissionsEntity
              .Read(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
            ApiKeyTemplatesPermissionsEntity
              .Read(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between these Users and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, _, userIds) = dataIds

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUserTemplateAssociations.transact(transactor)

          expectedEntities = List(
            ApiKeyTemplatesUsersEntity.Read(apiKeyTemplateId = templateIds.head, userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Read(apiKeyTemplateId = templateIds(1), userId = userIds.head),
            ApiKeyTemplatesUsersEntity.Read(apiKeyTemplateId = templateIds(1), userId = userIds(1))
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, resourceServerId, _, _, _) = dataIds

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield (res, resourceServerId)

        result.asserting { case (res, resourceServerId) =>
          val expectedEntities = List(
            permissionEntityRead_1.copy(id = res.head.id, resourceServerId = resourceServerId),
            permissionEntityRead_2.copy(id = res(1).id, resourceServerId = resourceServerId),
            permissionEntityRead_3.copy(id = res(2).id, resourceServerId = resourceServerId)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ResourceServers" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, resourceServerId, _, _, _) = dataIds

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield (res, tenantId, resourceServerId)

        result.asserting { case (res, tenantId, resourceServerId) =>
          val expectedEntities = List(resourceServerEntityRead_1.copy(id = resourceServerId, tenantId = tenantId))

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, templateIds, _, _) = dataIds

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
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
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            userEntityRead_1.copy(tenantId = tenantDbId_1),
            userEntityRead_2.copy(tenantId = tenantDbId_1),
            userEntityRead_3.copy(tenantId = tenantDbId_1)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this Tenant" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (tenantId, _, _, _, _) = dataIds

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
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

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionTemplateAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete associations between these Users and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUserTemplateAssociations.transact(transactor)

        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }

      "delete Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "delete ResourceServers" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }

      "delete ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }

      "delete Users" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }

      "delete the Tenant" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }
  }

}
