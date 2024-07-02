package apikeysteward.base

import java.time.{Clock, Instant, ZoneOffset}

trait FixedClock {
  val nowInstant: Instant = Instant.parse("2024-02-15T12:34:56Z")
  implicit def fixedClock: Clock = Clock.fixed(nowInstant, ZoneOffset.UTC)
}
