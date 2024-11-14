package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApplicationsTestData.publicApplicationId_1
import apikeysteward.base.testdata.TenantsTestData.tenantEntityRead_1
import apikeysteward.model.ApiKeyTemplate
import apikeysteward.repositories.db.ApiKeyTemplatesPermissionsDbSpec.{ApplicationDbId, PermissionDbId, TemplateDbId}
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApplicationRepositoryItSpec
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

  private val permissionRepository =
    new PermissionRepository(applicationDb, permissionDb, apiKeyTemplatesPermissionsDb)(transactor)

  private val repository =
    new ApplicationRepository(tenantDb, applicationDb, permissionDb, permissionRepository)(transactor)

  private object Queries extends DoobieCustomMeta {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllTenants: ConnectionIO[List[TenantEntity.Read]] =
      sql"SELECT * FROM tenant".query[TenantEntity.Read].stream.compile.toList

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

  private def insertPrerequisiteData(): IO[(ApplicationDbId, List[TemplateDbId], List[PermissionDbId])] =
    (for {
      dataIds <- ApiKeyTemplatesPermissionsDbSpec.insertPrerequisiteData(
        tenantDb,
        applicationDb,
        permissionDb,
        apiKeyTemplateDb
      )
      (_, templateIds, permissionIds) = dataIds

      preExistingEntities = List(
        ApiKeyTemplatesPermissionsEntity
          .Write(apiKeyTemplateId = templateIds.head, permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds.head),
        ApiKeyTemplatesPermissionsEntity
          .Write(apiKeyTemplateId = templateIds(1), permissionId = permissionIds(1))
      )
      _ <- apiKeyTemplatesPermissionsDb.insertMany(preExistingEntities)
    } yield dataIds).transact(transactor)

  "ApplicationRepository on delete" when {

    "there are Permissions in the DB for given inactive Application" should {

      "delete associations between these Permissions and ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicApplicationId_1)
          _ <- repository.delete(publicApplicationId_1)

          res <- Queries.getAllAssociations.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApiKeyTemplatesPermissionsEntity.Read])
      }

      "delete these Permissions" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicApplicationId_1)
          _ <- repository.delete(publicApplicationId_1)

          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "delete this Application" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicApplicationId_1)
          _ <- repository.delete(publicApplicationId_1)

          res <- Queries.getAllApplications.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }

      "NOT delete the Tenant" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicApplicationId_1)
          _ <- repository.delete(publicApplicationId_1)

          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting(res => res shouldBe List(tenantEntityRead_1.copy(id = res.head.id)))
      }

      "NOT delete ApiKeyTemplates" in {
        val result = for {
          _ <- insertPrerequisiteData()

          _ <- repository.deactivate(publicApplicationId_1)
          _ <- repository.delete(publicApplicationId_1)

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
    }
  }

}
