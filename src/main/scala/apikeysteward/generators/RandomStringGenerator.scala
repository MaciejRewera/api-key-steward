package apikeysteward.generators

import apikeysteward.config.ApiKeyConfig
import cats.effect.IO
import cats.effect.std.SecureRandom
import fs2.Stream

class RandomStringGenerator(apiKeyConfig: ApiKeyConfig) {

  private val StringLength: Int = {
    val length = apiKeyConfig.randomSectionLength

    if (length > 0) length
    else throw new IllegalArgumentException(s"Provided length is not greater than zero: $length")
  }

//  TODO: Consider reseeding or re-instantiating the PRNG periodically.
  private val PRNGsAmount: Int = 13
  private val rngIo: IO[SecureRandom[IO]] = SecureRandom.javaSecuritySecureRandom[IO](PRNGsAmount)

  def generate: IO[String] =
    for {
      rng <- rngIo
      result <- Stream
        .repeatEval(rng.nextAlphaNumeric.map(_.toString))
        .take(StringLength)
        .compile
        .string
    } yield result
}
