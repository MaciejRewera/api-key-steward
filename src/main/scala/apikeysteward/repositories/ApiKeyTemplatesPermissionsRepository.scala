package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError._
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesPermissionsDb, PermissionDb}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._

class ApiKeyTemplatesPermissionsRepository(
    apiKeyTemplateDb: ApiKeyTemplateDb,
    permissionDb: PermissionDb,
    apiKeyTemplatesPermissionsDb: ApiKeyTemplatesPermissionsDb
)(
    transactor: Transactor[IO]
) {

  def insertMany(
      publicTemplateId: ApiKeyTemplateId,
      publicPermissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsInsertionError, Unit]] =
    (for {
      templateId <- getTemplateId(publicTemplateId)
      permissionIds <- getPermissionIds(publicPermissionIds)

      entitiesToInsert = permissionIds.map(ApiKeyTemplatesPermissionsEntity.Write(templateId, _))

      _ <- EitherT(apiKeyTemplatesPermissionsDb.insertMany(entitiesToInsert))
    } yield ()).value.transact(transactor)

  def deleteMany(
      publicTemplateId: ApiKeyTemplateId,
      publicPermissionIds: List[PermissionId]
  ): IO[Either[ApiKeyTemplatesPermissionsDbError, Unit]] =
    (for {
      templateId <- getTemplateId(publicTemplateId)
      permissionIds <- getPermissionIds(publicPermissionIds)

      entitiesToDelete = permissionIds.map(ApiKeyTemplatesPermissionsEntity.Write(templateId, _))

      _ <- EitherT(
        apiKeyTemplatesPermissionsDb
          .deleteMany(entitiesToDelete)
          .map(_.left.map(_.asInstanceOf[ApiKeyTemplatesPermissionsDbError]))
      )
    } yield ()).value.transact(transactor)

  private def getTemplateId(
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[doobie.ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, Long] =
    EitherT
      .fromOptionF(
        apiKeyTemplateDb.getByPublicTemplateId(publicTemplateId),
        ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId)
      )
      .map(_.id)

  private def getPermissionIds(
      publicPermissionIds: List[PermissionId]
  ): EitherT[doobie.ConnectionIO, ReferencedPermissionDoesNotExistError, List[Long]] =
    publicPermissionIds.traverse { permissionId =>
      EitherT
        .fromOptionF(
          permissionDb.getByPublicPermissionId(permissionId),
          ReferencedPermissionDoesNotExistError(permissionId)
        )
        .map(_.id)
    }

}
