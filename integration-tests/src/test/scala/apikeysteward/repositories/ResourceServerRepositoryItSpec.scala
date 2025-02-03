package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesPermissionsTestData._
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeysPermissionsTestData._
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1, tenantEntityRead_1}
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ResourceServerRepositoryItSpec
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

  private val repository =
    new ResourceServerRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, permissionRepository)(
      transactor
    )

  private object Queries extends DoobieCustomMeta {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllTenants: ConnectionIO[List[TenantEntity.Read]] =
      sql"SELECT * FROM tenant".query[TenantEntity.Read].stream.compile.toList

    val getAllResourceServers: ConnectionIO[List[ResourceServerEntity.Read]] =
      sql"SELECT * FROM resource_server".query[ResourceServerEntity.Read].stream.compile.toList

    val getAllPermissions: ConnectionIO[List[PermissionEntity.Read]] =
      sql"SELECT * FROM permission".query[PermissionEntity.Read].stream.compile.toList

    val getAllApiKeyTemplates: doobie.ConnectionIO[List[ApiKeyTemplateEntity.Read]] =
      sql"SELECT * FROM api_key_template".query[ApiKeyTemplateEntity.Read].stream.compile.toList

    val getAllApiKeyTemplatesPermissionsAssociations: ConnectionIO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
      sql"SELECT * FROM api_key_templates_permissions"
        .query[ApiKeyTemplatesPermissionsEntity.Read]
        .stream
        .compile
        .toList

    val getAllApiKeys: ConnectionIO[List[ApiKeyEntity.Read]] =
      sql"SELECT id, tenant_id, created_at, updated_at FROM api_key".query[ApiKeyEntity.Read].stream.compile.toList

    val getAllApiKeyData: ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList

    val getAllApiKeysPermissionsAssociations: ConnectionIO[List[ApiKeysPermissionsEntity.Read]] =
      sql"SELECT * FROM api_keys_permissions".query[ApiKeysPermissionsEntity.Read].stream.compile.toList

  }

  private def insertPrerequisiteData(): IO[Unit] =
    TestDataInsertions
      .insertAll(
        tenantDb,
        userDb,
        resourceServerDb,
        permissionDb,
        apiKeyTemplateDb,
        apiKeyDb,
        apiKeyDataDb,
        apiKeyTemplatesPermissionsDb,
        apiKeyTemplatesUsersDb,
        apiKeysPermissionsDb
      )
      .transact(transactor)

  "ResourceServerRepository on delete" when {

    "given ResourceServer is active" should {

      "NOT delete associations between related Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyTemplatesPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyTemplatesPermissionsEntityWrite_1_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_1.toRead,
            apiKeyTemplatesPermissionsEntityWrite_2_2.toRead
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete associations between related Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeysPermissionsEntityWrite_1_1.toRead,
            apiKeysPermissionsEntityWrite_1_2.toRead,
            apiKeysPermissionsEntityWrite_1_3.toRead,
            apiKeysPermissionsEntityWrite_2_1.toRead,
            apiKeysPermissionsEntityWrite_3_2.toRead,
            apiKeysPermissionsEntityWrite_3_3.toRead
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2, apiKeyDataEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(permissionEntityRead_1, permissionEntityRead_2, permissionEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete this ResourceServer" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting { res =>
          res shouldBe List(resourceServerEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there are entities in the DB for given inactive ResourceServer" should {

      "delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyTemplatesPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete associations between related Permissions and ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeysPermissionsAssociations.transact(transactor)
        } yield res

        result.asserting(_ should contain theSameElementsAs List.empty[ApiKeysPermissionsEntity.Read])
      }

      "delete these Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "delete this ResourceServer" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }

      "NOT delete ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(
            apiKeyTemplateEntityRead_1.copy(tenantId = tenantDbId_1),
            apiKeyTemplateEntityRead_2.copy(tenantId = tenantDbId_1),
            apiKeyTemplateEntityRead_3.copy(tenantId = tenantDbId_1)
          )

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeys" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeys.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyEntityRead_1, apiKeyEntityRead_2, apiKeyEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete related ApiKeyData" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllApiKeyData.transact(transactor)
        } yield res

        result.asserting { res =>
          val expectedEntities = List(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2, apiKeyDataEntityRead_3)

          res should contain theSameElementsAs expectedEntities
        }
      }

      "NOT delete the Tenant" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(tenantEntityRead_1))
      }
    }
  }

}
