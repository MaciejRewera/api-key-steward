package apikeysteward.utils

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito
import org.mockito.MockitoSugar.{mock, times, verify, verifyNoMoreInteractions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

class RetrySpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private trait ActionWrapper {
    def run: IO[Either[Throwable, String]]
  }

  private val action = mock[ActionWrapper]

  override def beforeEach(): Unit =
    Mockito.reset(action)

  private val testException = new RuntimeException("Test Exception")

  "Retry on retry" when {

    "action succeeds on the first try" should {

      val maxRetries = 0
      val isWorthRetrying: Throwable => Boolean = (_: Throwable) => true

      "NOT execute action again" in {
        action.run returns IO.pure(Right[Throwable, String]("Success!"))

        for {
          _ <- Retry.retry(maxRetries, isWorthRetrying)(action.run)
          _ <- IO(verify(action).run)
        } yield ()
      }

      "return the action's result" in {
        action.run returns IO.pure(Right[Throwable, String]("Success!"))

        Retry.retry(maxRetries, isWorthRetrying)(action.run).asserting(_ shouldBe "Success!")
      }
    }

    "action fails, isWorthRetrying returns true, but maxRetries is zero" should {

      val maxRetries = 0
      val isWorthRetrying: Throwable => Boolean = (_: Throwable) => true

      "NOT execute action again" in {
        action.run returns IO.pure(Left[Throwable, String](testException))

        for {
          _ <- Retry.retry(maxRetries, isWorthRetrying)(action.run).attempt
          _ <- IO(verify(action).run)
          _ <- IO(verifyNoMoreInteractions(action))
        } yield ()
      }

      "return MaxNumberOfRetriesExceeded exception" in {
        action.run returns IO.pure(Left[Throwable, String](testException))

        Retry.retry(maxRetries, isWorthRetrying)(action.run).attempt.asserting { res =>
          res.isLeft shouldBe true
          res.left.value.getMessage should include(testException.getMessage)
          res.left.value.getMessage should include("Exceeded max number of retries on error: ")
        }
      }
    }

    "action fails, maxRetries is greater than zero, but isWorthRetrying returns false" should {

      val maxRetries = 3
      val isWorthRetrying: Throwable => Boolean = (_: Throwable) => false

      "NOT execute action again" in {
        action.run returns IO.pure(Left[Throwable, String](testException))

        for {
          _ <- Retry.retry(maxRetries, isWorthRetrying)(action.run).attempt
          _ <- IO(verify(action).run)
          _ <- IO(verifyNoMoreInteractions(action))
        } yield ()
      }

      "return ReceivedErrorNotConfiguredToRetry exception" in {
        action.run returns IO.pure(Left[Throwable, String](testException))

        Retry.retry(maxRetries, isWorthRetrying)(action.run).attempt.asserting { res =>
          res.isLeft shouldBe true
          res.left.value.getMessage should include(testException.getMessage)
          res.left.value.getMessage should include("Re-throwing error not configured for retrying: ")
        }
      }
    }

    "action fails continuously, isWorthRetrying returns true and maxRetries is greater than zero" should {

      val maxRetries = 3
      val isWorthRetrying: Throwable => Boolean = (_: Throwable) => true

      "execute action again up to maxRetries times" in {
        action.run returns IO.pure(Left[Throwable, String](testException))

        for {
          _ <- Retry.retry(maxRetries, isWorthRetrying)(action.run).attempt
          _ <- IO(verify(action, times(maxRetries + 1)).run)
          _ <- IO(verifyNoMoreInteractions(action))
        } yield ()
      }

      "return MaxNumberOfRetriesExceeded exception" in {
        action.run returns IO.pure(Left[Throwable, String](testException))

        Retry.retry(maxRetries, isWorthRetrying)(action.run).attempt.asserting { res =>
          res.isLeft shouldBe true
          res.left.value.getMessage should include(testException.getMessage)
          res.left.value.getMessage should include("Exceeded max number of retries on error: ")
        }
      }
    }

    "action fails several times before succeeding, isWorthRetrying returns true and maxRetries is greater than the amount of failed actions" should {

      val maxRetries = 3
      val isWorthRetrying: Throwable => Boolean = (_: Throwable) => true

      "execute action until it succeeds" in {
        action.run returns (
          IO.pure(Left[Throwable, String](testException)),
          IO.pure(Left[Throwable, String](testException)),
          IO.pure(Right[Throwable, String]("Success!"))
        )

        for {
          _ <- Retry.retry(maxRetries, isWorthRetrying)(action.run)
          _ <- IO(verify(action, times(3)).run)
          _ <- IO(verifyNoMoreInteractions(action))
        } yield ()
      }

      "return the action's result" in {
        action.run returns (
          IO.pure(Left[Throwable, String](testException)),
          IO.pure(Left[Throwable, String](testException)),
          IO.pure(Right[Throwable, String]("Success!"))
        )

        Retry.retry(maxRetries, isWorthRetrying)(action.run).asserting(_ shouldBe "Success!")
      }
    }
  }
}
