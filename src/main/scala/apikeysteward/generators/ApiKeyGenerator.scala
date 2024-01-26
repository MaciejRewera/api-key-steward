package apikeysteward.generators

import cats.effect.IO

trait ApiKeyGenerator[T] {
  def generateApiKey: IO[T]
}
