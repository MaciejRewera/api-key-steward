package apikeysteward.generators

import cats.effect.IO
import cats.effect.std.SecureRandom
import fs2.Stream

class RandomStringGenerator {

  private val DefaultApiKeyLength: Int = 50

//  TODO: Consider reseeding or re-instantiating the PRNG periodically.
  private val PRNGsAmount: Int = 13
  private val rngIo: IO[SecureRandom[IO]] = SecureRandom.javaSecuritySecureRandom[IO](PRNGsAmount)

  def generate: IO[String] =
    for {
      rng <- rngIo
      result <- Stream
        .repeatEval(rng.nextAlphaNumeric.map(_.toString))
        .take(DefaultApiKeyLength)
        .compile
        .string
    } yield result
}
