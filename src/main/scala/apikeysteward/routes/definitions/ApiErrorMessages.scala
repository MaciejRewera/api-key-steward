package apikeysteward.routes.definitions

import java.time.Instant

private[routes] object ApiErrorMessages {

  object General {
    val Unauthorized = "Credentials are invalid."
    val BadRequest = "Invalid value for body provided."
  }

  object Admin {
    val DeleteApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
    val GetAllApiKeysForUserNotFound = "No API Key found for provided userId."
  }

  object Management {
    val DeleteApiKeyNotFound = "No API Key found for provided keyId."
    val GetAllApiKeysNotFound = "No API Key found."
  }

  object ValidateApiKey {
    val ValidateApiKeyIncorrect = "Provided API Key is incorrect or does not exist."
    def validateApiKeyExpired(since: Instant): String = s"Provided API Key is expired since: $since."
  }

}
