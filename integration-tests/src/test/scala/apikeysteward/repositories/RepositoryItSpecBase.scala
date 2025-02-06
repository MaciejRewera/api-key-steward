package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

trait RepositoryItSpecBase
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with DoobieCustomMeta {

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

  val tenantDb = new TenantDb
  val resourceServerDb = new ResourceServerDb
  val permissionDb = new PermissionDb
  val apiKeyTemplatesPermissionsDb = new ApiKeyTemplatesPermissionsDb
  val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb
  val apiKeyTemplateDb = new ApiKeyTemplateDb
  val userDb = new UserDb
  val apiKeyDb = new ApiKeyDb
  val apiKeyDataDb = new ApiKeyDataDb
  val apiKeysPermissionsDb = new ApiKeysPermissionsDb

  object Queries {
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

    val getAllApiKeyTemplatesPermissionsAssociations: ConnectionIO[List[ApiKeyTemplatesPermissionsEntity.Read]] =
      sql"SELECT * FROM api_key_templates_permissions"
        .query[ApiKeyTemplatesPermissionsEntity.Read]
        .stream
        .compile
        .toList

    val getAllApiKeyTemplatesUsersAssociations: ConnectionIO[List[ApiKeyTemplatesUsersEntity.Read]] =
      sql"SELECT * FROM api_key_templates_users".query[ApiKeyTemplatesUsersEntity.Read].stream.compile.toList

    val getAllApiKeys: ConnectionIO[List[ApiKeyEntity.Read]] =
      sql"SELECT id, tenant_id, created_at, updated_at FROM api_key".query[ApiKeyEntity.Read].stream.compile.toList

    val getAllApiKeyData: ConnectionIO[List[ApiKeyDataEntity.Read]] =
      sql"SELECT * FROM api_key_data".query[ApiKeyDataEntity.Read].stream.compile.toList

    val getAllApiKeysPermissionsAssociations: ConnectionIO[List[ApiKeysPermissionsEntity.Read]] =
      sql"SELECT * FROM api_keys_permissions".query[ApiKeysPermissionsEntity.Read].stream.compile.toList

  }

  def insertPrerequisiteDataAll(): IO[Unit] =
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

}
