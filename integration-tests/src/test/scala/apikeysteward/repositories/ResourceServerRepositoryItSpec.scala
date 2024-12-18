package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ResourceServersTestData.{publicResourceServerId_1, resourceServerEntityRead_1}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1, tenantEntityRead_1}
import apikeysteward.model.ApiKeyTemplate
import apikeysteward.repositories.TestDataInsertions.{PermissionDbId, ResourceServerDbId, TemplateDbId, TenantDbId}
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
    _ <-
      sql"TRUNCATE tenant, resource_server, permission, api_key_template, api_key_templates_permissions CASCADE".update.run
  } yield ()

  private val uuidGenerator = new UuidGenerator
  private val tenantDb = new TenantDb
  private val resourceServerDb = new ResourceServerDb
  private val permissionDb = new PermissionDb
  private val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb

  private val permissionRepository =
    new PermissionRepository(uuidGenerator, tenantDb, resourceServerDb, permissionDb, apiKeyTemplatesPermissionsDb)(
      transactor
    )

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

    val getAllAssociations: ConnectionIO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
      sql"SELECT * FROM api_key_templates_permissions"
        .query[ApiKeyTemplatesPermissionsEntity.Read]
        .stream
        .compile
        .toList
  }

  private def insertPrerequisiteData(): IO[(TenantDbId, ResourceServerDbId, List[TemplateDbId], List[PermissionDbId])] =
    (for {
      dataIds <- TestDataInsertions.insertPrerequisiteTemplatesAndPermissions(
        tenantDb,
        resourceServerDb,
        permissionDb,
        apiKeyTemplateDb
      )
      (_, _, templateIds, permissionIds) = dataIds

      associationEntities = List(
        ApiKeyTemplatesPermissionsEntity
          .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(tenantId = tenantDbId_1, apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
      )
      _ <- apiKeyTemplatesPermissionsDb.insertMany(associationEntities)
    } yield dataIds).transact(transactor)

  "ResourceServerRepository on delete" when {

    "given ResourceServer is active" should {

      "NOT delete associations between related Permissions and ApiKeyTemplates" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, _, templateIds, permissionIds) = dataIds

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllAssociations.transact(transactor)

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

      "NOT delete related Permissions" in {
        val result = for {
          dataIds <- insertPrerequisiteData()
          (_, resourceServerId, _, _) = dataIds

          _ <- repository.activate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

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

          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
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
          res.map(ApiKeyTemplate.from(_, List.empty)) should contain theSameElementsAs List(
            apiKeyTemplate_1.copy(permissions = List.empty),
            apiKeyTemplate_2.copy(permissions = List.empty),
            apiKeyTemplate_3.copy(permissions = List.empty)
          )
        }
      }

      "NOT delete the Tenant" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- repository.delete(publicTenantId_1, publicResourceServerId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(res => res shouldBe List(tenantEntityRead_1.copy(id = res.head.id)))
      }
    }
  }

}
