package apikeysteward.generators

import apikeysteward.base.TestData.{apiKey_1, apiKey_2, apiKey_3, apiKey_4}
import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import apikeysteward.utils.Retry.RetryException.MaxNumberOfRetriesExceeded
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyGeneratorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val apiKeyPrefixProvider = mock[ApiKeyPrefixProvider]
  private val randomStringGenerator = mock[RandomStringGenerator]
  private val checksumCalculator = mock[CRC32ChecksumCalculator]

  private val apiKeyGenerator = new ApiKeyGenerator(apiKeyPrefixProvider, randomStringGenerator, checksumCalculator)

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyPrefixProvider, randomStringGenerator, checksumCalculator)
  }

  private val apiKeyPrefix: String = "testPrefix_"

  "ApiKeyGenerator on generateApiKey" when {

    "everything works correctly" should {

      "call RandomStringGenerator, CRC32ChecksumCalculator and ApiKeyPrefixProvider" in {
        randomStringGenerator.generate returns IO.pure(apiKey_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)

        for {
          _ <- apiKeyGenerator.generateApiKey

          _ = verify(randomStringGenerator).generate
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_1))
          _ = verify(apiKeyPrefixProvider).fetchPrefix

        } yield ()
      }

      "return the newly created Api Key" in {
        randomStringGenerator.generate returns IO.pure(apiKey_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)

        apiKeyGenerator.generateApiKey.asserting { result =>
          val expectedEncodedChecksum = "00000" + Base62.CharacterSet(42)

          result shouldBe (apiKeyPrefix + apiKey_1 + expectedEncodedChecksum)
        }
      }
    }

    "RandomStringGenerator returns failed IO" should {

      "NOT call either CRC32ChecksumCalculator or ApiKeyPrefixProvider" in {
        randomStringGenerator.generate returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt

          _ = verifyZeroInteractions(checksumCalculator, apiKeyPrefixProvider)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        randomStringGenerator.generate returns IO.raiseError(testException)

        apiKeyGenerator.generateApiKey.attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "CRC32ChecksumCalculator returns negative checksum on the first try" should {

      "call RandomStringGenerator, CRC32ChecksumCalculator again" in {
        randomStringGenerator.generate returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
        checksumCalculator.calcChecksumFor(any[String]) returns (-42L, 42L)
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)

        for {
          _ <- apiKeyGenerator.generateApiKey

          _ = verify(randomStringGenerator, times(2)).generate
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_1))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_2))
        } yield ()
      }

      "call ApiKeyPrefixProvider once" in {
        randomStringGenerator.generate returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
        checksumCalculator.calcChecksumFor(any[String]) returns (-42L, 42L)
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)

        for {
          _ <- apiKeyGenerator.generateApiKey

          _ = verify(apiKeyPrefixProvider).fetchPrefix
        } yield ()
      }

      "return the second created Api Key" in {
        randomStringGenerator.generate returns (IO.pure(apiKey_1), IO.pure(apiKey_2))
        checksumCalculator.calcChecksumFor(any[String]) returns (-42L, 42L)
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)

        apiKeyGenerator.generateApiKey.asserting { result =>
          val expectedEncodedChecksum = "00000" + Base62.CharacterSet(42)

          result shouldBe (apiKeyPrefix + apiKey_2 + expectedEncodedChecksum)
        }
      }
    }

    "CRC32ChecksumCalculator keeps returning negative checksum" should {

      "call RandomStringGenerator, CRC32ChecksumCalculator again until reaching max retries amount (3)" in {
        randomStringGenerator.generate returns (
          IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
        )
        checksumCalculator.calcChecksumFor(any[String]) returns -42L

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt

          _ = verify(randomStringGenerator, times(4)).generate
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_1))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_2))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_3))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKey_4))
        } yield ()
      }

      "NOT call ApiKeyPrefixProvider at all" in {
        randomStringGenerator.generate returns (
          IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
        )
        checksumCalculator.calcChecksumFor(any[String]) returns -42L
        for {
          _ <- apiKeyGenerator.generateApiKey.attempt

          _ = verifyZeroInteractions(apiKeyPrefixProvider)
        } yield ()
      }

      "return failed IO containing MaxNumberOfRetriesExceeded" in {
        randomStringGenerator.generate returns (
          IO.pure(apiKey_1), IO.pure(apiKey_2), IO.pure(apiKey_3), IO.pure(apiKey_4)
        )
        checksumCalculator.calcChecksumFor(any[String]) returns -42L

        apiKeyGenerator.generateApiKey.attempt.asserting(
          _ shouldBe Left(MaxNumberOfRetriesExceeded(ProvidedWithNegativeNumberError(-42L)))
        )
      }
    }

    "ApiKeyPrefixProvider returns failed IO" should {
      "return failed IO containing the same exception" in {
        randomStringGenerator.generate returns IO.pure(apiKey_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        apiKeyPrefixProvider.fetchPrefix returns IO.raiseError(testException)

        apiKeyGenerator.generateApiKey.attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
