package apikeysteward.repositories.db

import apikeysteward.model.CustomError

import java.util.UUID

object DbCommons {

  sealed abstract class ApiKeyInsertionError(override val message: String) extends CustomError
  object ApiKeyInsertionError {

    case object ApiKeyAlreadyExistsError extends ApiKeyInsertionError(message = "API Key already exists.")

    case object ApiKeyIdAlreadyExistsError
        extends ApiKeyInsertionError(message = "API Key Data with the same apiKeyId already exists.")
    case object PublicKeyIdAlreadyExistsError
        extends ApiKeyInsertionError(message = "API Key Data with the same publicKeyId already exists.")
  }

  // TODO: Update and Delete errors are not used in Db classes, so they should be moved to a different place.
  sealed abstract class ApiKeyUpdateError(override val message: String) extends CustomError
  object ApiKeyUpdateError {

    def apiKeyDataNotFoundError(userId: String, publicKeyId: UUID): ApiKeyUpdateError =
      ApiKeyDataNotFoundError(userId, publicKeyId)

    case class ApiKeyDataNotFoundError(userId: String, publicKeyId: UUID)
        extends ApiKeyUpdateError(
          message = s"Could not find API Key Data with userId = $userId and publicKeyId = $publicKeyId"
        )
  }

  sealed abstract class ApiKeyDeletionError(override val message: String) extends CustomError
  object ApiKeyDeletionError {

    case class ApiKeyDataNotFoundError(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message = s"Could not find API Key Data with userId = $userId and publicKeyId = $publicKeyId"
        )

    case class GenericApiKeyDeletionError(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message = s"Could not delete API Key with userId = $userId and publicKeyId = $publicKeyId"
        )
  }

  sealed abstract class TenantDbError(override val message: String) extends CustomError
  object TenantDbError {

    case class TenantNotFoundError(publicId: UUID)
      extends TenantDbError(message = s"Could not find Tenant with publicId = ${publicId.toString}")

    sealed abstract class TenantInsertionError(override val message: String) extends TenantDbError(message)
    object TenantInsertionError {
      case class TenantAlreadyExistsError(publicId: UUID)
        extends TenantInsertionError(message = s"Tenant with publicId = ${publicId.toString} already exists.")
    }

  }
}
