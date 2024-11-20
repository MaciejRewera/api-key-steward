package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
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

  def insertMany(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId,
      publicUserIds: List[UserId]
  ): IO[Either[ApiKeyTemplatesUsersInsertionError, Unit]] =
    (for {
      templateId <- getTemplateId(publicTemplateId)
      userIds <- getUserIds(publicTenantId, publicUserIds)

      entitiesToInsert = userIds.map(ApiKeyTemplatesUsersEntity.Write(templateId, _))

      _ <- EitherT(apiKeyTemplatesUsersDb.insertMany(entitiesToInsert))
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

  private def getUserIds(
      publicTenantId: TenantId,
      publicUserIds: List[UserId]
  ): EitherT[doobie.ConnectionIO, ReferencedUserDoesNotExistError, List[Long]] =
    publicUserIds.traverse { publicUserId =>
      EitherT
        .fromOptionF(
          userDb.getByPublicUserId(publicTenantId, publicUserId),
          ReferencedUserDoesNotExistError(publicUserId, publicTenantId)
        )
        .map(_.id)
    }

}
