package apikeysteward.utils

import apikeysteward.utils.Retry.RetryException._
import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Retry {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def retry[E, A](maxRetries: Int, isWorthRetrying: E => Boolean)(action: => IO[Either[E, A]]): IO[A] = {
    def retryOnLeft(err: E): IO[A] =
      if (isWorthRetrying(err)) {
        if (maxRetries > 0) {
          logger.warn(s"Retrying failed action. Attempts left: ${maxRetries - 1}") >>
            retry(maxRetries - 1, isWorthRetrying)(action)
        } else IO.raiseError(MaxNumberOfRetriesExceeded(err))
      } else {
        IO.raiseError(ReceivedErrorNotConfiguredToRetry(err))
      }

    action.flatMap {
      case Left(err)  => retryOnLeft(err)
      case Right(res) => IO(res)
    }
  }

  sealed trait RetryException extends RuntimeException {
    val message: String
    override def getMessage: String = message
  }

  object RetryException {
    case class MaxNumberOfRetriesExceeded[E](error: E) extends RetryException {
      override val message: String = s"Exceeded max number of retries on error: $error"
    }

    case class ReceivedErrorNotConfiguredToRetry[E](error: E) extends RetryException {
      override val message: String = s"Re-throwing error not configured for retrying: $error"
    }
  }
}
