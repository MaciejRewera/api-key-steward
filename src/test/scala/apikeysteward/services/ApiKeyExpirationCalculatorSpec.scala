package apikeysteward.services

import apikeysteward.base.FixedClock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ApiKeyExpirationCalculatorSpec extends AnyWordSpec with Matchers with FixedClock {

  private val truncationUnit = ChronoUnit.SECONDS

  "ApiKeyExpirationCalculator on calcExpiresAt" should {

    "return Instant with added 'ttl' minutes" in {
      val ttl = 42
      val expectedOutput = nowInstant.plus(ttl, TimeUnit.MINUTES.toChronoUnit).truncatedTo(truncationUnit)

      ApiKeyExpirationCalculator.calcExpiresAt(ttl) shouldBe expectedOutput
    }

    "return Instant truncated to seconds" in {
      val expectedOutput = nowInstant.truncatedTo(truncationUnit)

      ApiKeyExpirationCalculator.calcExpiresAt(0) shouldBe expectedOutput
    }
  }

}
