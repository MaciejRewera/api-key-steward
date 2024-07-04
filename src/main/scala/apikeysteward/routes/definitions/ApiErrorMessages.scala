package apikeysteward.routes.definitions

import java.time.Instant

private[routes] object ApiErrorMessages {

  object General {
    val InternalServerError = "An unexpected error has occurred."
    val Unauthorized = "Credentials are invalid."
    val BadRequest = "Invalid input value provided."
  }

  object Admin {
    val DeleteApiKeyNotFound = "No API Key found for provided combination of userId and keyId."
  }

  object Management {
    val DeleteApiKeyNotFound = "No API Key found for provided keyId."
  }

  object ValidateApiKey {
    val ValidateApiKeyIncorrect = "Provided API Key is incorrect or does not exist."
    def validateApiKeyExpired(since: Instant): String = s"Provided API Key is expired since: $since."
  }

}
