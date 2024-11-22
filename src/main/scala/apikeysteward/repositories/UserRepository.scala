package apikeysteward.repositories

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import fs2.Stream
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError.ReferencedTenantDoesNotExistError
import apikeysteward.model.RepositoryErrors.UserDbError.{UserInsertionError, UserNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.UserEntity
import apikeysteward.repositories.db.{TenantDb, UserDb}
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

class UserRepository(tenantDb: TenantDb, userDb: UserDb)(transactor: Transactor[IO]) {

  def insert(publicTenantId: TenantId, user: User): IO[Either[UserInsertionError, User]] =
    (for {
      tenantId <- EitherT
        .fromOptionF(
          tenantDb.getByPublicTenantId(publicTenantId),
          ReferencedTenantDoesNotExistError(publicTenantId)
        )
        .map(_.id)

      userEntityRead <- EitherT(userDb.insert(UserEntity.Write.from(tenantId, user)))

      resultUser = User.from(userEntityRead)
    } yield resultUser).value.transact(transactor)

  def delete(publicTenantId: TenantId, publicUserId: UserId): IO[Either[UserNotFoundError, User]] =
    deleteOp(publicTenantId, publicUserId).transact(transactor)

  private[repositories] def deleteOp(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): ConnectionIO[Either[UserNotFoundError, User]] =
    (for {
      userEntityRead <- EitherT(userDb.delete(publicTenantId, publicUserId))
      resultUser = User.from(userEntityRead)
    } yield resultUser).value

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

  def getAllForTemplate(publicTemplateId: ApiKeyTemplateId): IO[List[User]] =
    (for {
      userEntityRead <- userDb.getAllForTemplate(publicTemplateId)
      resultUser = User.from(userEntityRead)
    } yield resultUser).compile.toList.transact(transactor)

}
