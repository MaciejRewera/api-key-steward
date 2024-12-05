package apikeysteward.repositories.db

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError._
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.PermissionEntity
import cats.implicits.toTraverseOps
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import doobie.util.fragments.whereAndOpt
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}

class PermissionDb()(implicit clock: Clock) {

  def insert(
      permissionEntity: PermissionEntity.Write
  ): doobie.ConnectionIO[Either[PermissionInsertionError, PermissionEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(permissionEntity, now)
        .withUniqueGeneratedKeys[PermissionEntity.Read](
          "id",
          "tenant_id",
          "resource_server_id",
          "public_permission_id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, permissionEntity))

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      permissionEntity: PermissionEntity.Write
  ): PermissionInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_permission_id") =>
        PermissionAlreadyExistsError(permissionEntity.publicPermissionId)

      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("resource_server_id, name") =>
        PermissionAlreadyExistsForThisResourceServerError(permissionEntity.name, permissionEntity.resourceServerId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_tenant_id") =>
        ReferencedTenantDoesNotExistError.fromDbId(permissionEntity.tenantId)

      case FOREIGN_KEY_VIOLATION.value if sqlException.getMessage.contains("fkey_resource_server_id") =>
        ReferencedResourceServerDoesNotExistError.fromDbId(permissionEntity.resourceServerId)

      case _ => PermissionInsertionErrorImpl(sqlException)
    }

  def delete(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): doobie.ConnectionIO[Either[PermissionNotFoundError, PermissionEntity.Read]] =
    for {
      permissionToDeleteE <- getBy(publicTenantId, publicResourceServerId, publicPermissionId).map(
        _.toRight(PermissionNotFoundError(publicResourceServerId, publicPermissionId))
      )
      resultE <- permissionToDeleteE.traverse { result =>
        TenantIdScopedQueries(publicTenantId).delete(publicResourceServerId, publicPermissionId).run.map(_ => result)
      }
    } yield resultE

  def getBy(
      publicTenantId: TenantId,
      publicResourceServerId: ResourceServerId,
      publicPermissionId: PermissionId
  ): doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getBy(publicResourceServerId, publicPermissionId).option

  def getByPublicPermissionId(
      publicTenantId: TenantId,
      publicPermissionId: PermissionId
  ): doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    TenantIdScopedQueries(publicTenantId).getByPublicPermissionId(publicPermissionId).option

  def getAllForTemplate(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): Stream[doobie.ConnectionIO, PermissionEntity.Read] =
    TenantIdScopedQueries(publicTenantId).getAllForTemplate(publicTemplateId).stream

  def getAllBy(publicTenantId: TenantId, publicResourceServerId: ResourceServerId)(
      nameFragment: Option[String]
  ): Stream[doobie.ConnectionIO, PermissionEntity.Read] =
    TenantIdScopedQueries(publicTenantId).getAllBy(publicResourceServerId)(nameFragment).stream

  private object Queries {

    def insert(permissionEntity: PermissionEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO permission(id, tenant_id, resource_server_id, public_permission_id, name, description, created_at, updated_at)
            VALUES(
              ${permissionEntity.id},
              ${permissionEntity.tenantId},
              ${permissionEntity.resourceServerId},
              ${permissionEntity.publicPermissionId},
              ${permissionEntity.name},
              ${permissionEntity.description},
              $now,
              $now
            )
           """.stripMargin.update
  }

  private case class TenantIdScopedQueries(override val publicTenantId: TenantId) extends TenantIdScopedQueriesBase {

    private val TableName = "permission"

    def delete(publicResourceServerId: ResourceServerId, publicPermissionId: PermissionId): doobie.Update0 =
      sql"""DELETE FROM permission
            USING resource_server
            WHERE permission.resource_server_id = resource_server.id
              AND resource_server.public_resource_server_id = ${publicResourceServerId.toString}
              AND permission.public_permission_id = ${publicPermissionId.toString}
              AND ${tenantIdFr(TableName)}
           """.stripMargin.update

    def getBy(
        publicResourceServerId: ResourceServerId,
        publicPermissionId: PermissionId
    ): doobie.Query0[PermissionEntity.Read] =
      (PermissionDb.ColumnNamesSelectFragment ++
        sql"""FROM permission
              JOIN resource_server ON resource_server.id = permission.resource_server_id
              WHERE resource_server.public_resource_server_id = ${publicResourceServerId.toString}
                AND permission.public_permission_id = ${publicPermissionId.toString}
                AND ${tenantIdFr(TableName)}
             """).query[PermissionEntity.Read]

    def getByPublicPermissionId(publicPermissionId: PermissionId): doobie.Query0[PermissionEntity.Read] =
      (PermissionDb.ColumnNamesSelectFragment ++
        sql"""FROM permission
              WHERE permission.public_permission_id = ${publicPermissionId.toString}
                AND ${tenantIdFr(TableName)}
             """).query[PermissionEntity.Read]

    def getAllForTemplate(publicTemplateId: ApiKeyTemplateId): doobie.Query0[PermissionEntity.Read] =
      (PermissionDb.ColumnNamesSelectFragment ++
        sql"""FROM permission
              JOIN api_key_templates_permissions ON permission.id = api_key_templates_permissions.permission_id
              JOIN api_key_template ON api_key_template.id = api_key_templates_permissions.api_key_template_id
              WHERE api_key_template.public_template_id = ${publicTemplateId.toString}
                AND ${tenantIdFr(TableName)}
           """.stripMargin).query[PermissionEntity.Read]

    def getAllBy(
        publicResourceServerId: ResourceServerId
    )(nameFragment: Option[String]): doobie.Query0[PermissionEntity.Read] = {
      val publicResourceServerIdEquals = Some(
        fr"resource_server.public_resource_server_id = ${publicResourceServerId.toString}"
      )
      val nameLikeFr = nameFragment
        .map(n => s"%$n%")
        .map(n => fr"permission.name ILIKE $n")

      (PermissionDb.ColumnNamesSelectFragment ++
        sql"""FROM permission
              JOIN resource_server ON resource_server.id = permission.resource_server_id
              ${whereAndOpt(publicResourceServerIdEquals, nameLikeFr, Some(tenantIdFr(TableName)))}
              ORDER BY permission.name
             """.stripMargin).query[PermissionEntity.Read]
    }

  }
}

private[db] object PermissionDb {

  val ColumnNamesSelectFragment =
    fr"""SELECT
            permission.id,
            permission.tenant_id,
            permission.resource_server_id,
            permission.public_permission_id,
            permission.name,
            permission.description,
            permission.created_at,
            permission.updated_at
          """
}
