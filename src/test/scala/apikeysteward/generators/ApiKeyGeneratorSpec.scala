package apikeysteward.generators

import apikeysteward.base.TestData._
import apikeysteward.generators.Base62.Base62Error.ProvidedWithNegativeNumberError
import apikeysteward.model.ApiKey
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
  private val checksumCodec = mock[ChecksumCodec]

  private val apiKeyGenerator =
    new ApiKeyGenerator(apiKeyPrefixProvider, randomStringGenerator, checksumCalculator, checksumCodec)

  private val encodedValue_42 = "00000" + Base62.CharacterSet(42)

  private val testException = new RuntimeException("Test Exception")

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(apiKeyPrefixProvider, randomStringGenerator, checksumCalculator, checksumCodec)
  }

  "ApiKeyGenerator on generateApiKey" when {

    "everything works correctly" should {

      "call ApiKeyPrefixProvider, RandomStringGenerator, CRC32ChecksumCalculator and ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns IO.pure(apiKeyRandomFragment_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        for {
          _ <- apiKeyGenerator.generateApiKey

          _ = verify(apiKeyPrefixProvider).fetchPrefix
          _ = verify(randomStringGenerator).generate
          expectedApiKeyWithPrefix = apiKeyPrefix + apiKeyRandomFragment_1
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(expectedApiKeyWithPrefix))
          _ = verify(checksumCodec).encode(eqTo(42L))
        } yield ()
      }

      "return the newly created Api Key" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns IO.pure(apiKeyRandomFragment_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        apiKeyGenerator.generateApiKey.asserting { result =>
          result shouldBe ApiKey(apiKeyPrefix + apiKeyRandomFragment_1 + encodedValue_42)
        }
      }
    }

    "ApiKeyPrefixProvider returns failed IO" should {

      "call RandomStringGenerator anyway" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.raiseError(testException)
        randomStringGenerator.generate returns IO.pure(apiKeyRandomFragment_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt

          _ = verify(randomStringGenerator).generate
        } yield ()
      }

      "NOT call either CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.raiseError(testException)
        randomStringGenerator.generate returns IO.pure(apiKeyRandomFragment_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt

          _ = verifyZeroInteractions(checksumCalculator, checksumCodec)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.raiseError(testException)
        randomStringGenerator.generate returns IO.pure(apiKeyRandomFragment_1)
        checksumCalculator.calcChecksumFor(any[String]) returns 42L
        checksumCodec.encode(any[Long]) returns Right(encodedValue_42)

        apiKeyGenerator.generateApiKey.attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "RandomStringGenerator returns failed IO" should {

      "call ApiKeyPrefixProvider anyway" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt
          _ = verify(apiKeyPrefixProvider).fetchPrefix
        } yield ()
      }

      "NOT call either CRC32ChecksumCalculator or ChecksumCodec" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns IO.raiseError(testException)

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt
          _ = verifyZeroInteractions(checksumCalculator, checksumCodec)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns IO.raiseError(testException)

        apiKeyGenerator.generateApiKey.attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ChecksumCodec returns ProvidedWithNegativeNumberError on the first try" should {

      "call RandomStringGenerator, CRC32ChecksumCalculator and ChecksumCodec again" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns (IO.pure(apiKeyRandomFragment_1), IO.pure(apiKeyRandomFragment_2))
        checksumCalculator.calcChecksumFor(any[String]) returns (-42L, 42L)
        checksumCodec.encode(any[Long]) returns (
          Left(ProvidedWithNegativeNumberError(-42L)),
          Right(encodedValue_42)
        )

        for {
          _ <- apiKeyGenerator.generateApiKey

          _ = verify(randomStringGenerator, times(2)).generate
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomFragment_1))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomFragment_2))
        } yield ()
      }

      "NOT call ApiKeyPrefixProvider again" in {
        randomStringGenerator.generate returns (IO.pure(apiKeyRandomFragment_1), IO.pure(apiKeyRandomFragment_2))
        checksumCalculator.calcChecksumFor(any[String]) returns (-42L, 42L)
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        checksumCodec.encode(any[Long]) returns (
          Left(ProvidedWithNegativeNumberError(-42L)),
          Right(encodedValue_42)
        )

        for {
          _ <- apiKeyGenerator.generateApiKey
          _ = verify(apiKeyPrefixProvider).fetchPrefix
        } yield ()
      }

      "return the second created Api Key" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns (IO.pure(apiKeyRandomFragment_1), IO.pure(apiKeyRandomFragment_2))
        checksumCalculator.calcChecksumFor(any[String]) returns (-42L, 42L)
        checksumCodec.encode(any[Long]) returns (
          Left(ProvidedWithNegativeNumberError(-42L)),
          Right(encodedValue_42)
        )

        apiKeyGenerator.generateApiKey.asserting { result =>
          result shouldBe ApiKey(apiKeyPrefix + apiKeyRandomFragment_2 + encodedValue_42)
        }
      }
    }

    "ChecksumCodec keeps returning ProvidedWithNegativeNumberError" should {

      "call RandomStringGenerator, CRC32ChecksumCalculator and ChecksumCodec again until reaching max retries amount (3)" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns (
          IO.pure(apiKeyRandomFragment_1), IO.pure(apiKeyRandomFragment_2), IO.pure(apiKeyRandomFragment_3), IO.pure(
            apiKeyRandomFragment_4
          )
        )
        checksumCalculator.calcChecksumFor(any[String]) returns -42L
        checksumCodec.encode(any[Long]) returns Left(ProvidedWithNegativeNumberError(-42L))

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt

          _ = verify(randomStringGenerator, times(4)).generate
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomFragment_1))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomFragment_2))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomFragment_3))
          _ = verify(checksumCalculator).calcChecksumFor(eqTo(apiKeyPrefix + apiKeyRandomFragment_4))
        } yield ()
      }

      "NOT call ApiKeyPrefixProvider again" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns (
          IO.pure(apiKeyRandomFragment_1), IO.pure(apiKeyRandomFragment_2), IO.pure(apiKeyRandomFragment_3), IO.pure(
            apiKeyRandomFragment_4
          )
        )
        checksumCalculator.calcChecksumFor(any[String]) returns -42L
        checksumCodec.encode(any[Long]) returns Left(ProvidedWithNegativeNumberError(-42L))

        for {
          _ <- apiKeyGenerator.generateApiKey.attempt
          _ = verify(apiKeyPrefixProvider).fetchPrefix
        } yield ()
      }

      "return failed IO containing MaxNumberOfRetriesExceeded" in {
        apiKeyPrefixProvider.fetchPrefix returns IO.pure(apiKeyPrefix)
        randomStringGenerator.generate returns (
          IO.pure(apiKeyRandomFragment_1), IO.pure(apiKeyRandomFragment_2), IO.pure(apiKeyRandomFragment_3), IO.pure(
            apiKeyRandomFragment_4
          )
        )
        checksumCalculator.calcChecksumFor(any[String]) returns -42L
        checksumCodec.encode(any[Long]) returns Left(ProvidedWithNegativeNumberError(-42L))

        apiKeyGenerator.generateApiKey.attempt.asserting(
          _ shouldBe Left(MaxNumberOfRetriesExceeded(ProvidedWithNegativeNumberError(-42L)))
        )
      }
    }
  }

}
