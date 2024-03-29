package apikeysteward.repositories.db

import java.util.UUID

object DbCommons {

  sealed abstract class ApiKeyInsertionError(val message: String)
  object ApiKeyInsertionError {

    case object ApiKeyAlreadyExistsError extends ApiKeyInsertionError(message = "API Key already exists.")

    case object ApiKeyIdAlreadyExistsError
        extends ApiKeyInsertionError(message = "API Key Data with the same apiKeyId already exists.")
    case object PublicKeyIdAlreadyExistsError
        extends ApiKeyInsertionError(message = "API Key Data with the same publicKeyId already exists.")
  }

  sealed abstract class ApiKeyDeletionError(val message: String)
  object ApiKeyDeletionError {

    case class ApiKeyDataNotFound(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message = s"Could not find API Key Data with userId = $userId and publicKeyId = $publicKeyId"
        )

    case object GenericDeletionError
        extends ApiKeyDeletionError(message = "Something went wrong when deleting API Key.")

    case class CannotCopyApiKeyDataIntoDeletedTable(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message =
            s"Could not copy ApiKeyData with userId = $userId and publicKeyId = $publicKeyId into deleted table."
        )

    case class CannotDeleteApiKeyDataError(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message = s"Could not delete ApiKeyData with userId = $userId and publicKeyId = $publicKeyId"
        )
  }

}
