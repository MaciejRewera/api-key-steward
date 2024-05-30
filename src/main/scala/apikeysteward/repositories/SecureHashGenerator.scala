package apikeysteward.repositories

import apikeysteward.model.{ApiKey, HashedApiKey}
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import cats.effect.IO

import java.security.MessageDigest

class SecureHashGenerator(algorithm: Algorithm) {

  def generateHashFor(input: ApiKey): IO[HashedApiKey] = IO {
    val messageDigest = MessageDigest.getInstance(algorithm.name)
    val apiKeyHashedBytes = messageDigest.digest(input.value.toCharArray.map(_.toByte))

    HashedApiKey(apiKeyHashedBytes)
  }
}

object SecureHashGenerator {
  sealed trait Algorithm {
    def name: String
  }

  object Algorithm {
    case object SHA_224 extends Algorithm { override val name = "SHA-224" }
    case object SHA_256 extends Algorithm { override val name = "SHA-256" }
    case object SHA_384 extends Algorithm { override val name = "SHA-384" }
    case object SHA_512 extends Algorithm { override val name = "SHA-512" }

    case object SHA3_224 extends Algorithm { override val name = "SHA3-224" }
    case object SHA3_256 extends Algorithm { override val name = "SHA3-256" }
    case object SHA3_384 extends Algorithm { override val name = "SHA3-384" }
    case object SHA3_512 extends Algorithm { override val name = "SHA3-512" }

    val AllAlgorithms: Set[Algorithm] = Set(
      SHA_224,
      SHA_256,
      SHA_384,
      SHA_512,
      SHA3_224,
      SHA3_256,
      SHA3_384,
      SHA3_512
    )
  }
}
