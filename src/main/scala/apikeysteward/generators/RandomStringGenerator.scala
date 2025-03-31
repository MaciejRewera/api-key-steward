package apikeysteward.generators

import apikeysteward.config.ApiKeyConfig
import cats.effect.IO
import cats.effect.std.SecureRandom
import fs2.Stream

class RandomStringGenerator(apiKeyConfig: ApiKeyConfig) {

  private val PRNGsAmount: Int            = apiKeyConfig.prngAmount
  private val rngIo: IO[SecureRandom[IO]] = SecureRandom.javaSecuritySecureRandom[IO](PRNGsAmount)

  def generate(stringLength: Int): IO[String] =
    if (stringLength > 0) generateString(stringLength)
    else throw new IllegalArgumentException(s"Provided length is not greater than zero: $stringLength")

  private def generateString(stringLength: Int): IO[String] =
    for {
      rng <- rngIo
      result <- Stream
        .repeatEval(rng.nextAlphaNumeric.map(_.toString))
        .take(stringLength)
        .compile
        .string
    } yield result

}
