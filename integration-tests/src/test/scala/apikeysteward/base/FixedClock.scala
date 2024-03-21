package apikeysteward.base

import java.time.{Clock, Instant, ZoneOffset}

trait FixedClock {
  val now: Instant = Instant.parse("2024-02-15T12:34:56Z")
  implicit def fixedClock: Clock = Clock.fixed(now, ZoneOffset.UTC)
}
