package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData._
import apikeysteward.base.testdata.ApiKeysPermissionsTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import apikeysteward.repositories.TestDataInsertions._
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeysPermissionsDb, _}
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
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
    _ <- sql"""TRUNCATE
              |tenant,
              |tenant_user,
              |resource_server,
              |permission,
              |api_key_template,
              |api_key_templates_permissions,
              |api_key_templates_users,
              |api_key,
              |api_key_data,
              |api_keys_permissions
              |CASCADE""".stripMargin.update.run
  } yield ()

  private val uuidGenerator = new UuidGenerator
  private val tenantDb = new TenantDb
  private val resourceServerDb = new ResourceServerDb
  private val permissionDb = new PermissionDb
  private val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
  private val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val userDb = new UserDb
  private val apiKeyDb = new ApiKeyDb
  private val apiKeyDataDb = new ApiKeyDataDb
  private val apiKeysPermissionsDb = new ApiKeysPermissionsDb

  private val permissionRepository =
    new PermissionRepository(
      uuidGenerator,
      tenantDb,
      resourceServerDb,
      permissionDb,
      apiKeyTemplatesPermissionsDb,
      apiKeysPermissionsDb
    )(transactor)

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

  private val secureHashGenerator = new SecureHashGenerator(Algorithm.SHA3_256)
  private val apiKeyRepository = new ApiKeyRepository(
    uuidGenerator,
    secureHashGenerator,
    tenantDb,
    apiKeyDb,
    apiKeyDataDb,
    permissionDb,
    userDb,
    apiKeyTemplateDb,
    apiKeysPermissionsDb
  )(transactor)

  private val userRepository =
    new UserRepository(uuidGenerator, tenantDb, userDb, apiKeyTemplatesUsersDb, apiKeyRepository)(transactor)

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

    val getAllApiKeys: ConnectionIO[List[ApiKeyEntity.Read]] =
      sql"SELECT id, tenant_id, created_at, updated_at FROM api_key".query[ApiKeyEntity.Read].stream.compile.toList

    val getAllApiKeyData: ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList

    val getAllPermissionApiKeyAssociations: ConnectionIO[List[ApiKeysPermissionsEntity.Read]] =
      sql"SELECT * FROM api_keys_permissions".query[ApiKeysPermissionsEntity.Read].stream.compile.toList

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
        apiKeyTemplatesPermissionsEntityWrite_1_1,
        apiKeyTemplatesPermissionsEntityWrite_2_1,
        apiKeyTemplatesPermissionsEntityWrite_2_2
      )
      _ <- apiKeyTemplatesPermissionsDb.insertMany(associationEntities)

      userId_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
      userId_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
      userId_3 <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId)).map(_.value.id)
      userIds = List(userId_1, userId_2, userId_3)

      associationEntities = List(
        apiKeyTemplatesUsersEntityWrite_1_1,
        apiKeyTemplatesUsersEntityWrite_2_1,
        apiKeyTemplatesUsersEntityWrite_2_2
      )
      _ <- apiKeyTemplatesUsersDb.insertMany(associationEntities)

      _ <- apiKeyDb.insert(apiKeyEntityWrite_1)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_1)
      _ <- apiKeyDb.insert(apiKeyEntityWrite_2)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_2)
      _ <- apiKeyDb.insert(apiKeyEntityWrite_3)
      _ <- apiKeyDataDb.insert(apiKeyDataEntityWrite_3)

      associationEntities = List(
        apiKeysPermissionsEntityWrite_1_1,
        apiKeysPermissionsEntityWrite_1_2,
        apiKeysPermissionsEntityWrite_1_3,
        apiKeysPermissionsEntityWrite_2_1,
        apiKeysPermissionsEntityWrite_3_2,
        apiKeysPermissionsEntityWrite_3_3
      )
      _ <- apiKeysPermissionsDb.insertMany(associationEntities)

    } yield (tenantId, resourceServerId, templateIds, permissionIds, userIds)).transact(transactor)

  "TenantRepository on delete" when {

    "given Tenant is active" should {

      "NOT delete associations between Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionTemplateAssociations.transact(transactor)

          expectedEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_2.toRead
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between Users and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUserTemplateAssociations.transact(transactor)

          expectedEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1.toRead,
            apiKeyTemplatesUsersEntityWrite_2_1.toRead,
            apiKeyTemplatesUsersEntityWrite_2_2.toRead
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionApiKeyAssociations.transact(transactor)

          expectedEntities = List(
            apiKeysPermissionsEntityWrite_1_1.toRead,
            apiKeysPermissionsEntityWrite_1_2.toRead,
            apiKeysPermissionsEntityWrite_1_3.toRead,
            apiKeysPermissionsEntityWrite_2_1.toRead,
            apiKeysPermissionsEntityWrite_3_2.toRead,
            apiKeysPermissionsEntityWrite_3_3.toRead
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyEntityRead_1,
            apiKeyEntityRead_2,
            apiKeyEntityRead_3
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyDataEntityRead_1,
            apiKeyDataEntityRead_2,
            apiKeyDataEntityRead_3
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            permissionEntityRead_1,
            permissionEntityRead_2,
            permissionEntityRead_3
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ResourceServers" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.activate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(resourceServerEntityRead_1.copy(deactivatedAt = Some(nowInstant)))

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
          _ <- insertPrerequisiteData()

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

      "NOT delete associations between Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionTemplateAssociations.transact(transactor)

          expectedEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_2.toRead
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between Users and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUserTemplateAssociations.transact(transactor)

          expectedEntities = List(
            apiKeyTemplatesUsersEntityWrite_1_1.toRead,
            apiKeyTemplatesUsersEntityWrite_2_1.toRead,
            apiKeyTemplatesUsersEntityWrite_2_2.toRead
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionApiKeyAssociations.transact(transactor)

          expectedEntities = List(
            apiKeysPermissionsEntityWrite_1_1.toRead,
            apiKeysPermissionsEntityWrite_1_2.toRead,
            apiKeysPermissionsEntityWrite_1_3.toRead,
            apiKeysPermissionsEntityWrite_2_1.toRead,
            apiKeysPermissionsEntityWrite_3_2.toRead,
            apiKeysPermissionsEntityWrite_3_3.toRead
          )
        } yield (res, expectedEntities)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyEntityRead_1,
            apiKeyEntityRead_2,
            apiKeyEntityRead_3
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyDataEntityRead_1,
            apiKeyDataEntityRead_2,
            apiKeyDataEntityRead_3
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            permissionEntityRead_1,
            permissionEntityRead_2,
            permissionEntityRead_3
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

      "delete associations between Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionTemplateAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete associations between Users and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllUserTemplateAssociations.transact(transactor)

        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesUsersEntity.Read])
      }

      "delete associations between Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllPermissionApiKeyAssociations.transact(transactor)

        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeysPermissionsEntity.Read])
      }

      "delete ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyEntity.Read])
      }

      "delete all ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- resourceServerRepository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.deactivate(publicTenantId_1)
          _ <- repository.delete(publicTenantId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
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
