package apikeysteward.generators

import apikeysteward.config.ApiKeyConfig
import cats.effect.IO

class ApiKeyPrefixProvider(apiKeyConfig: ApiKeyConfig) {

  def fetchPrefix: IO[String] = IO.pure(apiKeyConfig.prefix)
}
