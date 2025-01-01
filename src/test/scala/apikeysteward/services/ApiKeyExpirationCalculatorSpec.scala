package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData.ttl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class ApiKeyExpirationCalculatorSpec extends AnyWordSpec with Matchers with FixedClock {

  private val truncationUnit = ChronoUnit.SECONDS

  "ApiKeyExpirationCalculator on calcExpiresAt" should {

    "return Instant with added 'ttl' minutes" in {
      val expectedOutput = nowInstant.plus(ttl.length, ttl.unit.toChronoUnit).truncatedTo(truncationUnit)

      ApiKeyExpirationCalculator.calcExpiresAtFromNow(ttl) shouldBe expectedOutput
    }

    "return Instant truncated to seconds" in {
      val expectedOutput = nowInstant.truncatedTo(truncationUnit)

      ApiKeyExpirationCalculator.calcExpiresAtFromNow(Duration.Zero) shouldBe expectedOutput
    }
  }

}
