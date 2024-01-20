package apikeysteward.generators

import cats.effect.IO
import cats.effect.std.SecureRandom

class StringApiKeyGenerator extends ApiKeyGenerator[String] {
  override def generateApiKey(length: Int): IO[String] =
    SecureRandom.javaSecuritySecureRandom[IO].flatMap(_.nextString(length))
}
