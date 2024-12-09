package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.errors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesUsersDb, TenantDb, UserDb}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class ApiKeyTemplatesUsersRepository(
    tenantDb: TenantDb,
    apiKeyTemplateDb: ApiKeyTemplateDb,
    userDb: UserDb,
    apiKeyTemplatesUsersDb: ApiKeyTemplatesUsersDb
)(transactor: Transactor[IO]) {

  def insertManyUsers(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId,
      publicUserIds: List[UserId]
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] =
    (for {
      tenantId <- getTenantId(publicTenantId)
      templateId <- getSingleTemplateId(publicTenantId, publicTemplateId)
      userIds <- getUserIds(publicTenantId, publicUserIds)

      entitiesToInsert = userIds.map(ApiKeyTemplatesUsersEntity.Write(tenantId, templateId, _))

      _ <- EitherT(apiKeyTemplatesUsersDb.insertMany(entitiesToInsert))
    } yield ()).value.transact(transactor)

  def insertManyTemplates(
      publicTenantId: TenantId,
      publicUserId: UserId,
      publicTemplateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] =
    (for {
      tenantId <- getTenantId(publicTenantId)
      userId <- getSingleUserId(publicTenantId, publicUserId)
      templateIds <- getTemplateIds(publicTenantId, publicTemplateIds)

      entitiesToInsert = templateIds.map(ApiKeyTemplatesUsersEntity.Write(tenantId, _, userId))

      _ <- EitherT(apiKeyTemplatesUsersDb.insertMany(entitiesToInsert))
    } yield ()).value.transact(transactor)

  def deleteManyTemplates(
      publicTenantId: TenantId,
      publicUserId: UserId,
      publicTemplateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersDbError, Unit]] =
    (for {
      tenantId <- getTenantId(publicTenantId)
      userId <- getSingleUserId(publicTenantId, publicUserId)
      templateIds <- getTemplateIds(publicTenantId, publicTemplateIds)

      entitiesToDelete = templateIds.map(ApiKeyTemplatesUsersEntity.Write(tenantId, _, userId))

      _ <- EitherT(
        apiKeyTemplatesUsersDb
          .deleteMany(entitiesToDelete)
          .map(_.left.map(_.asInstanceOf[ApiKeyTemplatesUsersDbError]))
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

  private def getTemplateIds(
      publicTenantId: TenantId,
      publicTemplateIds: List[ApiKeyTemplateId]
  ): EitherT[doobie.ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, List[UUID]] =
    publicTemplateIds.traverse(getSingleTemplateId(publicTenantId, _))

  private def getSingleTemplateId(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[doobie.ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, UUID] =
    EitherT
      .fromOptionF(
        apiKeyTemplateDb.getByPublicTemplateId(publicTenantId, publicTemplateId),
        ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId)
      )
      .map(_.id)

  private def getUserIds(
      publicTenantId: TenantId,
      publicUserIds: List[UserId]
  ): EitherT[doobie.ConnectionIO, ReferencedUserDoesNotExistError, List[UUID]] =
    publicUserIds.traverse(getSingleUserId(publicTenantId, _))

  private def getSingleUserId(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): EitherT[doobie.ConnectionIO, ReferencedUserDoesNotExistError, UUID] =
    EitherT
      .fromOptionF(
        userDb.getByPublicUserId(publicTenantId, publicUserId),
        ReferencedUserDoesNotExistError(publicUserId, publicTenantId)
      )
      .map(_.id)

}
