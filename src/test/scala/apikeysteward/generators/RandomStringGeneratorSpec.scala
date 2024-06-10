package apikeysteward.generators

import apikeysteward.config.ApiKeyConfig
import apikeysteward.repositories.SecureHashGenerator.Algorithm.SHA3_256
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.Stream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class RandomStringGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  "RandomStringGenerator on generate" should {

    val config = ApiKeyConfig(
      randomSectionLength = 42,
      prefix = "prefix",
      storageHashingAlgorithm = SHA3_256
    )

    "return String with provided length" in {
      val generator = new RandomStringGenerator(config)

      generator.generate.asserting(_.length shouldBe config.randomSectionLength)
    }

    "return different Strings on subsequent calls" in {
      val generator = new RandomStringGenerator(config)

      for {
        randomStrings <- Stream.repeatEval(generator.generate).take(42).compile.toVector

        _ = randomStrings.foreach(_.length shouldBe config.randomSectionLength)
        _ = randomStrings.distinct should contain theSameElementsAs randomStrings
      } yield ()
    }

    "throw exception when provided with length equal to zero" in {
      val incorrectConfig = config.copy(randomSectionLength = 0)

      a[RuntimeException] shouldBe thrownBy(new RandomStringGenerator(incorrectConfig))
    }

    "throw exception when provided with negative length" in {
      val incorrectConfig = config.copy(randomSectionLength = -1)

      a[IllegalArgumentException] shouldBe thrownBy(new RandomStringGenerator(incorrectConfig))
    }
  }
}
