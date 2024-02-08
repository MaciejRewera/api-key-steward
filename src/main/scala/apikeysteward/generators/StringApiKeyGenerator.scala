package apikeysteward.generators

import cats.effect.IO
import cats.effect.std.SecureRandom
import fs2.Stream

class StringApiKeyGenerator extends ApiKeyGenerator[String] {

  private val DefaultApiKeyLength: Int = 50
  private val PRNGsAmount: Int = 13

  // TODO: Consider reseeding or re-instantiating the PRNG periodically.
  private val rngIo: IO[SecureRandom[IO]] = SecureRandom.javaSecuritySecureRandom[IO](PRNGsAmount)

  override def generateApiKey: IO[String] =
    for {
      rng <- rngIo
      result <- Stream
        .repeatEval(rng.nextAlphaNumeric.map(_.toString))
        .take(DefaultApiKeyLength)
        .compile
        .string
    } yield result
}
