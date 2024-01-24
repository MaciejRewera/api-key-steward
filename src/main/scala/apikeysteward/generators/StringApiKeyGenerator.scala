package apikeysteward.generators

import cats.effect.IO
import cats.effect.std.SecureRandom
import fs2.Stream

class StringApiKeyGenerator extends ApiKeyGenerator[String] {

  // TODO: Consider reseeding or re-instantiating the PRNG periodically.

  private val PRNGsAmount: Int = 13
  private val rngIo: IO[SecureRandom[IO]] = SecureRandom.javaSecuritySecureRandom[IO](PRNGsAmount)

  override def generateApiKey(length: Int): IO[String] =
    for {
      rng <- rngIo
      result <- Stream
        .repeatEval(rng.nextAlphaNumeric.map(_.toString))
        .take(length)
        .compile
        .string
    } yield result
}
