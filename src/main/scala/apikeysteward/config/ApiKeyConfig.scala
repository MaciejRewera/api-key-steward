package apikeysteward.config

import apikeysteward.repositories.SecureHashGenerator.Algorithm
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ApiKeyConfig(
    randomSectionLength: Int,
    prefix: String,
    allowedScopes: Set[String],
    ttlMax: FiniteDuration,
    storageHashingAlgorithm: Algorithm
)

object ApiKeyConfig {
  implicit val hashingAlgorithmReader: ConfigReader[Algorithm] = {
    1.minute
    ConfigReader.fromString { str =>
      Algorithm.AllAlgorithms
        .find(_.name == str)
        .toRight(
          CannotConvert(
            str,
            Algorithm.getClass.getSimpleName,
            "Provided value does not match any of the available algorithms."
          )
        )
    }
  }

  implicit val apiKeyConfigReader: ConfigReader[ApiKeyConfig] = deriveReader[ApiKeyConfig]
}
