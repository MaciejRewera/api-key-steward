package apikeysteward.services

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit

object ApiKeyExpirationCalculator {

  val TtlTimeUnit: TimeUnit = TimeUnit.MINUTES
  val ApiKeyExpirationResolution: ChronoUnit = ChronoUnit.SECONDS

  def calcExpiresAt(timeToLive: Int)(implicit clock: Clock): Instant =
    Instant
      .now(clock)
      .plus(timeToLive, ApiKeyExpirationCalculator.TtlTimeUnit.toChronoUnit)
      .truncatedTo(ApiKeyExpirationResolution)
}
