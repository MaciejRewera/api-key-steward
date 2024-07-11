package apikeysteward.utils

import apikeysteward.model.CustomError
import apikeysteward.utils.Retry.RetryException._
import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Retry {

  private val logger: StructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def retry[E <: CustomError, A](maxRetries: Int, isWorthRetrying: E => Boolean)(
      action: => IO[Either[E, A]]
  ): IO[A] = {

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

  sealed abstract class RetryException[E <: CustomError](val error: E, val message: String)
      extends RuntimeException {
    override def getMessage: String = message
  }

  object RetryException {
    case class MaxNumberOfRetriesExceeded[E <: CustomError](override val error: E)
        extends RetryException[E](error, message = s"Exceeded max number of retries on error: ${error.message}")

    case class ReceivedErrorNotConfiguredToRetry[E <: CustomError](override val error: E)
        extends RetryException[E](error, message = s"Re-throwing error not configured for retrying: ${error.message}")
  }
}
