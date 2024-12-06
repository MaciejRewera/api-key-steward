package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesPermissionsDb, PermissionDb, TenantDb}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ApiKeyTemplatesPermissionsRepository(
    tenantDb: TenantDb,
    apiKeyTemplateDb: ApiKeyTemplateDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb
)(transactor: Transactor[IO]) {

  def insertMany(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId,
      publicPermissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsInsertionError, Unit]] =
    (for {
      tenantId <- getTenantId(publicTenantId)
      templateId <- getTemplateId(publicTenantId, publicTemplateId)
      permissionIds <- getPermissionIds(publicTenantId, publicPermissionIds)

      entitiesToInsert = permissionIds.map(ApiKeyTemplatesPermissionsEntity.Write(tenantId, templateId, _))

      _ <- EitherT(apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert))
    } yield ()).value.transact(transactor)

  def deleteMany(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId,
      publicPermissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsDbError, Unit]] =
    (for {
      tenantId <- getTenantId(publicTenantId)
      templateId <- getTemplateId(publicTenantId, publicTemplateId)
      permissionIds <- getPermissionIds(publicTenantId, publicPermissionIds)

      entitiesToDelete = permissionIds.map(ApiKeyTemplatesPermissionsEntity.Write(tenantId, templateId, _))

      _ <- EitherT(
        apiKeyTemplatesPermissionsDb
          .deleteMany(entitiesToDelete)
          .map(_.left.map(_.asInstanceOf[ApiKeyTemplatesPermissionsDbError]))
      )
    } yield ()).value.transact(transactor)

  private def getTenantId(
      publicTenantId: TenantId
  ): EitherT[doobie.ConnectionIO, ReferencedTenantDoesNotExistError, UUID] =
    EitherT
      .fromOptionF(
        tenantDb.getByPublicTenantId(publicTenantId),
        ReferencedTenantDoesNotExistError(publicTenantId)
      )
      .map(_.id)

  private def getTemplateId(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[doobie.ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, UUID] =
    EitherT
      .fromOptionF(
        apiKeyTemplateDb.getByPublicTemplateId(publicTenantId, publicTemplateId),
        ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId)
      )
      .map(_.id)

  private def getPermissionIds(
      publicTenantId: TenantId,
      publicPermissionIds: List[PermissionId]
  ): EitherT[doobie.ConnectionIO, ReferencedPermissionDoesNotExistError, List[UUID]] =
    publicPermissionIds.traverse { permissionId =>
      EitherT
        .fromOptionF(
          permissionDb.getByPublicPermissionId(publicTenantId, permissionId),
          ReferencedPermissionDoesNotExistError(permissionId)
        )
        .map(_.id)
    }

}
