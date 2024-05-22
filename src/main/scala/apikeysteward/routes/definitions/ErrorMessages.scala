package apikeysteward.routes.definitions

object ErrorMessages {

  object General {
    val Unauthorized = "Credentials are invalid."
  }

  object Admin {
    val DeleteApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
    val GetAllApiKeysForUserNotFound = "No API Key found for provided userId."
  }

  object ValidateApiKey {
    val ValidateApiKeyIncorrect = "Provided API Key is incorrect or does not exist."
  }

}
