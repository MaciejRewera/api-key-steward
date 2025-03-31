package apikeysteward.services

import apikeysteward.model.ApiKeyData.ApiKeyTtlResolution

import java.time.{Clock, Instant}
import scala.concurrent.duration.Duration

object ApiKeyExpirationCalculator {

  def calcExpiresAtFromNow(timeToLive: Duration)(implicit clock: Clock): Instant =
    Instant
      .now(clock)
      .plus(timeToLive.length, timeToLive.unit.toChronoUnit)
      .truncatedTo(ApiKeyTtlResolution.toChronoUnit)

}
