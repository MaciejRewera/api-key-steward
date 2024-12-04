package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity
import apikeysteward.repositories.db.{ApiKeyTemplatesUsersDb, TenantDb, UserDb}
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import fs2.Stream

import java.util.UUID

class UserRepository(
    uuidGenerator: UuidGenerator,
    tenantDb: TenantDb,
    userDb: UserDb,
    apiKeyTemplatesUsersDb: ApiKeyTemplatesUsersDb
)(transactor: Transactor[IO]) {

  def insert(publicTenantId: TenantId, user: User): IO[Either[UserInsertionError, User]] =
    for {
      userDbId <- uuidGenerator.generateUuid
      result <- insert(userDbId, publicTenantId, user)
    } yield result

  private def insert(userDbId: UUID, publicTenantId: TenantId, user: User): IO[Either[UserInsertionError, User]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(
          tenantDb.getByPublicTenantId(publicTenantId),
          ReferencedTenantDoesNotExistError(publicTenantId)
        )
        .map(_.id)

      userEntityRead <- EitherT(userDb.insert(UserEntity.Write.from(userDbId, tenantId, user)))

      resultUser = User.from(userEntityRead)
    } yield resultUser).value.transact(transactor)

  def delete(publicTenantId: TenantId, publicUserId: UserId): IO[Either[UserNotFoundError, User]] =
    deleteOp(publicTenantId, publicUserId).transact(transactor)

  private[repositories] def deleteOp(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): ConnectionIO[Either[UserNotFoundError, User]] =
    for {
      _ <- apiKeyTemplatesUsersDb.deleteAllForUser(publicTenantId, publicUserId)
      userEntityRead <- userDb.delete(publicTenantId, publicUserId)

      resultUser = userEntityRead.map(User.from)
    } yield resultUser

  def getBy(publicTenantId: TenantId, publicUserId: UserId): IO[Option[User]] =
    (for {
      userEntityRead <- OptionT(userDb.getByPublicUserId(publicTenantId, publicUserId))
      resultUser = User.from(userEntityRead)
    } yield resultUser).value.transact(transactor)

  def getAllForTenant(publicTenantId: TenantId): IO[List[User]] =
    getAllForTenantOp(publicTenantId).compile.toList.transact(transactor)

  private[repositories] def getAllForTenantOp(publicTenantId: TenantId): Stream[ConnectionIO, User] =
    for {
      userEntityRead <- userDb.getAllForTenant(publicTenantId)
      resultUser = User.from(userEntityRead)
    } yield resultUser

  def getAllForTemplate(publicTenantId: TenantId, publicTemplateId: ApiKeyTemplateId): IO[List[User]] =
    (for {
      userEntityRead <- userDb.getAllForTemplate(publicTenantId, publicTemplateId)
      resultUser = User.from(userEntityRead)
    } yield resultUser).compile.toList.transact(transactor)

}
