package apikeysteward.generators

import apikeysteward.config.ApiKeyConfig
import apikeysteward.repositories.SecureHashGenerator.Algorithm.SHA3_256
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class RandomStringGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  "RandomStringGenerator on generate" should {

    val config = ApiKeyConfig(
      randomPartLength = 42, prefix = "prefix", storageHashingAlgorithm = SHA3_256
    )

    "return String with provided length" in {
      val generator = new RandomStringGenerator(config)

      generator.generate.asserting(_.length shouldBe config.randomPartLength)
    }

    "throw exception when provided with length equal to zero" in {
      val incorrectConfig = config.copy(randomPartLength = 0)

      a[RuntimeException] shouldBe thrownBy { new RandomStringGenerator(incorrectConfig) }
    }

    "throw exception when provided with negative length" in {
      val incorrectConfig = config.copy(randomPartLength = -1)

      a[IllegalArgumentException] shouldBe thrownBy { new RandomStringGenerator(incorrectConfig) }
    }
  }
}
