package apikeysteward.utils

import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Logging { self =>
  val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]
}
