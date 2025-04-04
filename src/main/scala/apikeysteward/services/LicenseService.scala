package apikeysteward.services

import apikeysteward.config.LicenseConfig
import apikeysteward.license.LicenseValidator
import apikeysteward.utils.Logging
import cats.effect.IO
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

class LicenseService(
    config: LicenseService.Configuration,
    licenseValidator: LicenseValidator
) extends Logging {

  def periodicallyValidateLicense(): IO[Unit] =
    (
      Stream.eval(logger.info(s"Scheduling license validation in ${config.initialDelay}.")) ++
        Stream.sleep_[IO](config.initialDelay) ++
        Stream
          .repeatEval(licenseValidator.isValid(config.licenseConfig.licenseKey))
          .evalTap {
            case true  => logger.info(s"License valid. Scheduling next validation in ${config.validationPeriod}.")
            case false => logger.warn("License invalid. Shutting down.")
          }
          .metered(config.validationPeriod)
          .takeThrough(identity)
    ).compile.drain

}

object LicenseService {

  case class Configuration(
      initialDelay: FiniteDuration,
      validationPeriod: FiniteDuration,
      licenseConfig: LicenseConfig
  )

}
