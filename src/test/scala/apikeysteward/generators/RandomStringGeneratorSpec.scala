package apikeysteward.generators

import apikeysteward.config.ApiKeyConfig
import apikeysteward.repositories.SecureHashGenerator.Algorithm.SHA3_256
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.Stream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class RandomStringGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  private val config = ApiKeyConfig(
    prngAmount = 13,
    storageHashingAlgorithm = SHA3_256
  )

  private val randomSectionLength = 42

  private val generator = new RandomStringGenerator(config)

  "RandomStringGenerator on generate" should {

    "return String with provided length" in
      generator.generate(randomSectionLength).asserting(_.length shouldBe randomSectionLength)

    "return different Strings on subsequent calls" in {
      for {
        randomStrings <- Stream.repeatEval(generator.generate(randomSectionLength)).take(13).compile.toVector

        _ = randomStrings.foreach(_.length shouldBe randomSectionLength)
        _ = randomStrings.distinct should contain theSameElementsAs randomStrings
      } yield ()
    }

    "throw exception when provided with length equal to zero" in {
      a[RuntimeException] shouldBe thrownBy(generator.generate(0))
    }

    "throw exception when provided with negative length" in {
      a[IllegalArgumentException] shouldBe thrownBy(generator.generate(-1))
    }
  }

}
