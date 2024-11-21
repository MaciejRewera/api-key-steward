package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity
import apikeysteward.repositories.db.{ApiKeyTemplateDb, ApiKeyTemplatesUsersDb, UserDb}
import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toTraverseOps
import doobie.Transactor
import doobie.implicits._

class ApiKeyTemplatesUsersRepository(
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
      templateId <- getSingleTemplateId(publicTemplateId)
      userIds <- getUserIds(publicTenantId, publicUserIds)

      entitiesToInsert = userIds.map(ApiKeyTemplatesUsersEntity.Write(templateId, _))

      _ <- EitherT(apiKeyTemplatesUsersDb.insertMany(entitiesToInsert))
    } yield ()).value.transact(transactor)

  def insertManyTemplates(
      publicTenantId: TenantId,
      publicUserId: UserId,
      publicTemplateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] =
    (for {
      userId <- getSingleUserId(publicTenantId, publicUserId)
      templateIds <- getTemplateIds(publicTemplateIds)

      entitiesToInsert = templateIds.map(ApiKeyTemplatesUsersEntity.Write(_, userId))

      _ <- EitherT(apiKeyTemplatesUsersDb.insertMany(entitiesToInsert))
    } yield ()).value.transact(transactor)

  def deleteManyTemplates(
      publicTenantId: TenantId,
      publicUserId: UserId,
      publicTemplateIds: List[ApiKeyTemplateId]
  ): IO[Either[ApiKeyTemplatesUsersDbError, Unit]] =
    (for {
      userId <- getSingleUserId(publicTenantId, publicUserId)
      templateIds <- getTemplateIds(publicTemplateIds)

      entitiesToDelete = templateIds.map(ApiKeyTemplatesUsersEntity.Write(_, userId))

      _ <- EitherT(
        apiKeyTemplatesUsersDb
          .deleteMany(entitiesToDelete)
          .map(_.left.map(_.asInstanceOf[ApiKeyTemplatesUsersDbError]))
      )
    } yield ()).value.transact(transactor)

  private def getTemplateIds(
      publicTemplateIds: List[ApiKeyTemplateId]
  ): EitherT[doobie.ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, List[Long]] =
    publicTemplateIds.traverse(getSingleTemplateId)

  private def getSingleTemplateId(
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[doobie.ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, Long] =
    EitherT
      .fromOptionF(
        apiKeyTemplateDb.getByPublicTemplateId(publicTemplateId),
        ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId)
      )
      .map(_.id)

  private def getUserIds(
      publicTenantId: TenantId,
      publicUserIds: List[UserId]
  ): EitherT[doobie.ConnectionIO, ReferencedUserDoesNotExistError, List[Long]] =
    publicUserIds.traverse(getSingleUserId(publicTenantId, _))

  private def getSingleUserId(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): EitherT[doobie.ConnectionIO, ReferencedUserDoesNotExistError, Long] =
    EitherT
      .fromOptionF(
        userDb.getByPublicUserId(publicTenantId, publicUserId),
        ReferencedUserDoesNotExistError(publicUserId, publicTenantId)
      )
      .map(_.id)

}
