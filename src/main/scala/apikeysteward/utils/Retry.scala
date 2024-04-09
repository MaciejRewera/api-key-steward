package apikeysteward.utils

import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Retry {

  private def logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def retry[E, A](maxRetries: Int, isWorthRetrying: E => Boolean)(action: => IO[Either[E, A]]): IO[A] = {
    def retryOnLeft(err: E): IO[A] =
      if (isWorthRetrying(err)) {
        if (maxRetries > 0) {
          logger.warn(s"Retrying failed action. Attempts left: ${maxRetries - 1}") *>
            retry(maxRetries - 1, isWorthRetrying)(action)
        } else IO.raiseError(new RuntimeException(s"Exceeded max number of retries on error: $err"))
      } else {
        IO.raiseError(new RuntimeException(s"Re-throwing error not configured for retrying: $err"))
      }

    action.flatMap {
      case Left(err)  => retryOnLeft(err)
      case Right(res) => IO(res)
    }
  }

}
