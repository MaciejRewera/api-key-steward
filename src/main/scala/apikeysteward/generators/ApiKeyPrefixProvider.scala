package apikeysteward.generators

import cats.effect.IO

class ApiKeyPrefixProvider {

  private val prefix = "steward_"

  def fetchPrefix: IO[String] = IO.pure(prefix)
}
