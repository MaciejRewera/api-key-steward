package apikeysteward.services

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object ApiKeyExpirationCalculator {

  private val ApiKeyExpirationResolution: ChronoUnit = ChronoUnit.SECONDS

  def calcExpiresAtFromNow(timeToLive: Duration)(implicit clock: Clock): Instant =
    Instant
      .now(clock)
      .plus(timeToLive.length, timeToLive.unit.toChronoUnit)
      .truncatedTo(ApiKeyExpirationResolution)
}
