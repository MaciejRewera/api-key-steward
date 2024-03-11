package apikeysteward.utils

import cats.effect.IO

object Retry {

  def retry[E, A](maxRetries: Int, isWorthRetrying: E => Boolean)(action: => IO[Either[E, A]]): IO[A] = {
    def retryOnLeft(err: E): IO[A] =
      if (isWorthRetrying(err)) {
        if (maxRetries > 0) retry(maxRetries - 1, isWorthRetrying)(action)
        else IO.raiseError(new RuntimeException(s"Exceeded max number of retries on error: $err"))
      } else {
        IO.raiseError(new RuntimeException(s"Re-throwing error not configured for retrying: $err"))
      }

    action.flatMap {
      case Left(err)  => retryOnLeft(err)
      case Right(res) => IO(res)
    }
  }

}
