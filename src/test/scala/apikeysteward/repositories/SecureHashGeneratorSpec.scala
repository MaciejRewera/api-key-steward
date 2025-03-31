package apikeysteward.repositories

import apikeysteward.model.{ApiKey, HashedApiKey}
import apikeysteward.repositories.SecureHashGenerator.Algorithm
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.security.MessageDigest
import scala.util.Random

class SecureHashGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  private def bytesToHex(bytes: Array[Byte]): String = {
    val out = new StringBuilder()
    bytes.foreach(b => out.append(String.format("%02X", b)))
    out.toString
  }

  "SecureHashGenerator on generateHashFor" when {

    val input = Random.nextString(30)

    Algorithm.AllAlgorithms.foreach { algorithm =>
      s"provided with $algorithm algorithm" should {

        "return correct value" in {
          val secureHashGenerator = new SecureHashGenerator(algorithm)

          val expectedResult = {
            val messageDigest     = MessageDigest.getInstance(algorithm.name)
            val apiKeyHashedBytes = messageDigest.digest(input.toCharArray.map(_.toByte))
            HashedApiKey(bytesToHex(apiKeyHashedBytes))
          }

          secureHashGenerator.generateHashFor(ApiKey(input)).asserting(_ shouldBe expectedResult)
        }

        "always return the same output when provided with the same input" in {
          val secureHashGenerator = new SecureHashGenerator(algorithm)

          for {
            hash_1 <- secureHashGenerator.generateHashFor(ApiKey(input))
            hash_2 <- secureHashGenerator.generateHashFor(ApiKey(input))

            _ = hash_1 shouldBe hash_2
          } yield ()
        }
      }
    }
  }

}
