package apikeysteward.utils

import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Logging {
  val logger: StructuredLogger[IO] = Slf4jLogger.getLoggerFromClass(getClass)
}
