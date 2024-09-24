package apikeysteward.services

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit

object ApiKeyExpirationCalculator {

  val ttlTimeUnit: TimeUnit = TimeUnit.MINUTES

  def calcExpiresAt(timeToLive: Int)(implicit clock: Clock): Instant =
    Instant.now(clock).plus(timeToLive, ApiKeyExpirationCalculator.ttlTimeUnit.toChronoUnit)
}
