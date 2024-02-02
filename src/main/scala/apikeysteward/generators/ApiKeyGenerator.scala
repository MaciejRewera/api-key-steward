package apikeysteward.generators

import cats.effect.IO

trait ApiKeyGenerator[K] {
  def generateApiKey: IO[K]
}
