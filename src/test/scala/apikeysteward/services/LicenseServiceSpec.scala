package apikeysteward.services

import apikeysteward.config.LicenseConfig
import apikeysteward.license.LicenseValidator
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify, verifyNoMoreInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration.DurationInt

class LicenseServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val licenseValidator = mock[LicenseValidator]

  private val LicenseKey = "TEST-LICENSE-KEY"
  private val config = LicenseService.Configuration(
    initialDelay = 0.millis,
    validationPeriod = 0.millis,
    licenseConfig = LicenseConfig(LicenseKey)
  )

  private val licenseService = new LicenseService(config, licenseValidator)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(licenseValidator)
  }

  "LicenseService on periodicallyValidateLicense" when {

    "should always call LicenseValidator" in {
      licenseValidator.isValid(any[String]) returns IO.pure(false)

      for {
        _ <- licenseService.periodicallyValidateLicense()

        _ = verify(licenseValidator).isValid(eqTo(LicenseKey))
      } yield ()
    }

    "LicenseValidator returns true" should {

      "call LicenseValidator only once (it's IO program that is called multiple times, not LicenseValidator method)" in {
        val it = Seq(true, false).iterator
        licenseValidator.isValid(any[String]) returns IO(it.next())

        for {
          _ <- licenseService.periodicallyValidateLicense()

          _ = verify(licenseValidator).isValid(eqTo(LicenseKey))
          _ = verifyNoMoreInteractions(licenseValidator)
        } yield ()
      }
    }

    "LicenseValidator returns false" should {

      "call LicenseValidator only once" in {
        licenseValidator.isValid(any[String]) returns IO.pure(false)

        for {
          _ <- licenseService.periodicallyValidateLicense()

          _ = verify(licenseValidator).isValid(eqTo(LicenseKey))
          _ = verifyNoMoreInteractions(licenseValidator)
        } yield ()
      }

      "return completed IO" in {
        licenseValidator.isValid(any[String]) returns IO.pure(false)

        licenseService.periodicallyValidateLicense().asserting(_ shouldBe ())
      }
    }

    "LicenseValidator returns true several times before returning false" should {

      "call LicenseValidator only once (it's IO program that is called multiple times, not LicenseValidator method)" in {
        val it = Seq(true, true, true, true, true, true, true, true, false).iterator
        licenseValidator.isValid(any[String]) returns IO(it.next())

        for {
          _ <- licenseService.periodicallyValidateLicense()

          _ = verify(licenseValidator).isValid(eqTo(LicenseKey))
          _ = verifyNoMoreInteractions(licenseValidator)
        } yield ()
      }

      "return completed IO" in {
        val it = Seq(true, true, true, true, true, true, true, true, false).iterator
        licenseValidator.isValid(any[String]) returns IO(it.next())

        licenseService.periodicallyValidateLicense().asserting(_ shouldBe ())
      }
    }
  }

}
