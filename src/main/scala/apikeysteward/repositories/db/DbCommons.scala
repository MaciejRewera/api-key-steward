package apikeysteward.repositories.db

object DbCommons {

  sealed abstract class ApiKeyInsertionError(val message: String)
  object ApiKeyInsertionError {

    case object ApiKeyAlreadyExistsError extends ApiKeyInsertionError(message = "API Key already exists.")

    case object ApiKeyIdAlreadyExistsError
        extends ApiKeyInsertionError(message = "API Key Data with the same apiKeyId already exists.")
    case object PublicKeyIdAlreadyExistsError
        extends ApiKeyInsertionError(message = "API Key Data with the same publicKeyId already exists.")
  }
}
