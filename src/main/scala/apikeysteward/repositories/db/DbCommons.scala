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

  sealed abstract class ApiKeyDeletionError(override val message: String) extends CustomError
  object ApiKeyDeletionError {

    case class ApiKeyDataNotFound(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message = s"Could not find API Key Data with userId = $userId and publicKeyId = $publicKeyId"
        )

    case class GenericApiKeyDeletionError(userId: String, publicKeyId: UUID)
        extends ApiKeyDeletionError(
          message = s"Could not delete API Key with userId = $userId and publicKeyId = $publicKeyId"
        )
  }

}
