package apikeysteward.repositories

import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.ApiKeyDbError._
import apikeysteward.model.{ApiKey, ApiKeyData, ApiKeyDataUpdate, HashedApiKey}
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import fs2.Stream
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

class ApiKeyRepository(
    uuidGenerator: UuidGenerator,
    secureHashGenerator: SecureHashGenerator,
    tenantDb: TenantDb,
    apiKeyDb: ApiKeyDb,
    apiKeyDataDb: ApiKeyDataDb,
    permissionDb: PermissionDb,
    userDb: UserDb,
    apiKeyTemplateDb: ApiKeyTemplateDb,
    apiKeysPermissionsDb: ApiKeysPermissionsDb
)(transactor: Transactor[IO]) {

  private val logger: StructuredLogger[doobie.ConnectionIO] = Slf4jLogger.getLogger

  def insert(
      publicTenantId: TenantId,
      apiKey: ApiKey,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyDbError, ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyDbId <- uuidGenerator.generateUuid
      apiKeyDataDbId <- uuidGenerator.generateUuid

      result <- insertHashed(publicTenantId, hashedApiKey, apiKeyDbId, apiKeyDataDbId, apiKeyData)
    } yield result

  private def insertHashed(
      publicTenantId: TenantId,
      hashedApiKey: HashedApiKey,
      apiKeyDbId: UUID,
      apiKeyDataDbId: UUID,
      apiKeyData: ApiKeyData
  ): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      tenantDbId <- getTenantEntity(publicTenantId).map(_.id)
      userDbId <- getUserEntity(publicTenantId, apiKeyData.publicUserId).map(_.id)
      templateDbId <- getApiKeyTemplateEntityByPublicId(publicTenantId, apiKeyData.publicTemplateId).map(_.id)

      permissionDbIds <-
        apiKeyData.permissions.map(_.publicPermissionId).traverse { permissionId =>
          EitherT
            .fromOptionF(
              permissionDb.getByPublicPermissionId(publicTenantId, permissionId),
              ReferencedPermissionDoesNotExistError(permissionId)
            )
            .map(_.id)
        }

      _ <- logInfoE("Inserting new API Key...")
      apiKeyEntityRead <- EitherT(apiKeyDb.insert(ApiKeyEntity.Write(apiKeyDbId, tenantDbId, hashedApiKey.value)))
        .leftSemiflatTap(e => logger.warn(s"Could not insert API Key because: ${e.message}"))
        .flatTap(_ => logInfoE("Inserted new API Key."))

      apiKeyId = apiKeyEntityRead.id

      _ <- logInfoE(s"Inserting API Key Data for publicKeyId: [${apiKeyData.publicKeyId}]...")
      apiKeyDataEntityWrite = ApiKeyDataEntity.Write.from(
        id = apiKeyDataDbId,
        tenantId = tenantDbId,
        apiKeyId = apiKeyId,
        userId = userDbId,
        templateId = templateDbId,
        apiKeyData = apiKeyData
      )

      apiKeyDataEntityRead <- EitherT(apiKeyDataDb.insert(apiKeyDataEntityWrite)).flatTap(_ =>
        logInfoE(s"Inserted API Key Data for publicKeyId: [${apiKeyData.publicKeyId}].")
      )

      permissionEntitiesRead <- EitherT(insertPermissionAssociations(tenantDbId, apiKeyDataDbId, permissionDbIds))
        .flatTap(_ =>
          logInfoE(s"Inserted API Key - Permissions associations for publicKeyId: [${apiKeyData.publicKeyId}].")
        )

      apiKeyData <- constructApiKeyData(publicTenantId, apiKeyDataEntityRead)
    } yield apiKeyData).value.transact(transactor)

  private def insertPermissionAssociations(
      tenantDbId: UUID,
      apiKeyDataDbId: UUID,
      permissionDbIds: List[UUID]
  ): ConnectionIO[Either[ApiKeyDbError, List[ApiKeysPermissionsEntity.Read]]] = {
    val entitiesToInsert = permissionDbIds.map { permissionDbId =>
      ApiKeysPermissionsEntity.Write(
        tenantId = tenantDbId,
        apiKeyDataId = apiKeyDataDbId,
        permissionId = permissionDbId
      )
    }

    apiKeysPermissionsDb
      .insertMany(entitiesToInsert)
      .map(_.left.map(ApiKeyPermissionAssociationCannotBeCreated))
  }

  def update(publicTenantId: TenantId, apiKeyDataUpdate: ApiKeyDataUpdate): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      _ <- logInfoE[ApiKeyDbError](
        s"Updating API Key Data for key with publicKeyId: [${apiKeyDataUpdate.publicKeyId}]..."
      )
      entityAfterUpdateRead <- EitherT(
        apiKeyDataDb.update(publicTenantId, ApiKeyDataEntity.Update.from(apiKeyDataUpdate))
      ).flatTap(_ =>
        logInfoE[ApiKeyDbError](s"Updated API Key Data for key with publicKeyId: [${apiKeyDataUpdate.publicKeyId}].")
      )

      apiKeyData <- constructApiKeyData(publicTenantId, entityAfterUpdateRead)
    } yield apiKeyData).value.transact(transactor)

  def get(publicTenantId: TenantId, apiKey: ApiKey): IO[Option[ApiKeyData]] =
    for {
      hashedApiKey <- secureHashGenerator.generateHashFor(apiKey)
      apiKeyData <- getByHashed(publicTenantId, hashedApiKey)
    } yield apiKeyData

  private def getByHashed(publicTenantId: TenantId, hashedApiKey: HashedApiKey): IO[Option[ApiKeyData]] =
    (for {
      apiKeyEntityRead <- OptionT(apiKeyDb.getByApiKey(publicTenantId, hashedApiKey))
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByApiKeyId(publicTenantId, apiKeyEntityRead.id))

      apiKeyData <- constructApiKeyData(publicTenantId, apiKeyDataEntityRead).toOption
    } yield apiKeyData).value.transact(transactor)

  def getAllForUser(publicTenantId: TenantId, userId: UserId): IO[List[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- apiKeyDataDb.getByUserId(publicTenantId, userId)
      apiKeyData <- Stream.eval(constructApiKeyData(publicTenantId, apiKeyDataEntityRead).toOption.value)
    } yield apiKeyData).transact(transactor).compile.toList.map(_.flatten)

  def get(publicTenantId: TenantId, userId: UserId, publicKeyId: ApiKeyId): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getBy(publicTenantId, userId, publicKeyId))
      apiKeyData <- constructApiKeyData(publicTenantId, apiKeyDataEntityRead).toOption
    } yield apiKeyData).value.transact(transactor)

  def getByPublicKeyId(publicTenantId: TenantId, publicKeyId: ApiKeyId): IO[Option[ApiKeyData]] =
    (for {
      apiKeyDataEntityRead <- OptionT(apiKeyDataDb.getByPublicKeyId(publicTenantId, publicKeyId))
      apiKeyData <- constructApiKeyData(publicTenantId, apiKeyDataEntityRead).toOption
    } yield apiKeyData).value.transact(transactor)

  def delete(
      publicTenantId: TenantId,
      userId: UserId,
      publicKeyIdToDelete: ApiKeyId
  ): IO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDelete <- EitherT {
        apiKeyDataDb
          .getBy(publicTenantId, userId, publicKeyIdToDelete)
          .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(userId, publicKeyIdToDelete)))
      }

      deletionResult <- performDeletion(publicTenantId, apiKeyDataToDelete)
    } yield deletionResult).value.transact(transactor)

  def delete(publicTenantId: TenantId, publicKeyIdToDelete: ApiKeyId): IO[Either[ApiKeyDbError, ApiKeyData]] =
    deleteOp(publicTenantId, publicKeyIdToDelete).transact(transactor)

  private[repositories] def deleteOp(
      publicTenantId: TenantId,
      publicKeyIdToDelete: ApiKeyId
  ): ConnectionIO[Either[ApiKeyDbError, ApiKeyData]] =
    (for {
      apiKeyDataToDelete <- EitherT {
        apiKeyDataDb
          .getByPublicKeyId(publicTenantId, publicKeyIdToDelete)
          .map(_.toRight(ApiKeyDbError.apiKeyDataNotFoundError(publicKeyIdToDelete)))
      }

      deletionResult <- performDeletion(publicTenantId, apiKeyDataToDelete)
    } yield deletionResult).value

  private[repositories] def deleteAllForUser(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): ConnectionIO[Either[ApiKeyDbError, List[ApiKeyData]]] =
    for {
      apiKeyDataIdsToDelete <- apiKeyDataDb
        .getByUserId(publicTenantId, publicUserId)
        .map(_.publicKeyId)
        .map(UUID.fromString)
        .compile
        .toList
      res <- apiKeyDataIdsToDelete.traverse(deleteOp(publicTenantId, _))
    } yield res.sequence

  private def performDeletion(
      publicTenantId: TenantId,
      apiKeyDataToDelete: ApiKeyDataEntity.Read
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyData] =
    for {
      res <- constructApiKeyData(publicTenantId, apiKeyDataToDelete)

      publicKeyIdToDelete = UUID.fromString(apiKeyDataToDelete.publicKeyId)
      _ <- deletePermissionAssociations(publicTenantId, publicKeyIdToDelete)
      _ <- deleteApiKeyData(publicTenantId, publicKeyIdToDelete)
      _ <- deleteApiKey(publicTenantId, apiKeyDataToDelete.apiKeyId, publicKeyIdToDelete)
    } yield res

  private def deletePermissionAssociations(
      publicTenantId: TenantId,
      publicKeyIdToDelete: ApiKeyId
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, Int] =
    EitherT.liftF(apiKeysPermissionsDb.deleteAllForApiKey(publicTenantId, publicKeyIdToDelete))

  private def deleteApiKeyData(
      publicTenantId: TenantId,
      publicKeyIdToDelete: ApiKeyId
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyDataEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKeyData for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDataDb.delete(publicTenantId, publicKeyIdToDelete)
      _ <- logger.info(s"Deleted ApiKeyData for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private def deleteApiKey(
      publicTenantId: TenantId,
      apiKeyId: UUID,
      publicKeyIdToDelete: ApiKeyId
  ): EitherT[doobie.ConnectionIO, ApiKeyDbError, ApiKeyEntity.Read] =
    EitherT(for {
      _ <- logger.info(s"Deleting ApiKey for key with publicKeyId: [$publicKeyIdToDelete]...")
      res <- apiKeyDb.delete(publicTenantId, apiKeyId)
      _ <- logger.info(s"Deleted ApiKey for key with publicKeyId: [$publicKeyIdToDelete].")
    } yield res)

  private[repositories] def constructApiKeyData(
      publicTenantId: TenantId,
      apiKeyDataEntity: ApiKeyDataEntity.Read
  ): EitherT[ConnectionIO, ApiKeyDbError, ApiKeyData] =
    (for {
      userEntity <- getUserEntity(publicTenantId, apiKeyDataEntity.userId)
      templateEntity <- getApiKeyTemplateEntity(publicTenantId, apiKeyDataEntity.templateId)
      permissionEntities <- getPermissionEntities(publicTenantId, UUID.fromString(apiKeyDataEntity.publicKeyId))

      resultApiKeyData = ApiKeyData.from(
        userEntity.publicUserId,
        UUID.fromString(templateEntity.publicTemplateId),
        apiKeyDataEntity,
        permissionEntities
      )
    } yield resultApiKeyData).leftSemiflatTap { error =>
      logger.warn(s"Could not construct API Key Data, because: ${error.message}")
    }

  private def getTenantEntity(
      publicTenantId: TenantId
  ): EitherT[ConnectionIO, ReferencedTenantDoesNotExistError, TenantEntity.Read] =
    EitherT.fromOptionF(
      tenantDb.getByPublicTenantId(publicTenantId),
      ReferencedTenantDoesNotExistError(publicTenantId)
    )

  private def getUserEntity(
      publicTenantId: TenantId,
      publicUserId: UserId
  ): EitherT[ConnectionIO, ReferencedUserDoesNotExistError, UserEntity.Read] =
    EitherT.fromOptionF(
      userDb.getByPublicUserId(publicTenantId, publicUserId),
      ReferencedUserDoesNotExistError(publicUserId)
    )

  private def getUserEntity(
      publicTenantId: TenantId,
      userDbId: UUID
  ): EitherT[ConnectionIO, ReferencedUserDoesNotExistError, UserEntity.Read] =
    EitherT.fromOptionF(
      userDb.getByDbId(publicTenantId, userDbId),
      ReferencedUserDoesNotExistError.fromDbId(userDbId)
    )

  private def getApiKeyTemplateEntityByPublicId(
      publicTenantId: TenantId,
      publicTemplateId: ApiKeyTemplateId
  ): EitherT[ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, ApiKeyTemplateEntity.Read] =
    EitherT.fromOptionF(
      apiKeyTemplateDb.getByPublicTemplateId(publicTenantId, publicTemplateId),
      ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId)
    )

  private def getApiKeyTemplateEntity(
      publicTenantId: TenantId,
      templateDbId: UUID
  ): EitherT[ConnectionIO, ReferencedApiKeyTemplateDoesNotExistError, ApiKeyTemplateEntity.Read] =
    EitherT.fromOptionF(
      apiKeyTemplateDb.getByDbId(publicTenantId, templateDbId),
      ReferencedApiKeyTemplateDoesNotExistError.fromDbId(templateDbId)
    )

  private def getPermissionEntities(
      publicTenantId: TenantId,
      publicKeyId: ApiKeyId
  ): EitherT[ConnectionIO, ApiKeyDbError, List[PermissionEntity.Read]] =
    EitherT.right[ApiKeyDbError] {
      permissionDb
        .getAllForApiKey(publicTenantId, publicKeyId)
        .compile
        .toList
    }

  private def logInfoE[E](message: String): EitherT[doobie.ConnectionIO, E, Unit] = EitherT.right(logger.info(message))
}
