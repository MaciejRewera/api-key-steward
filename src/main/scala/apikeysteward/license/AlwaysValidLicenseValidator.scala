package apikeysteward.license

import cats.effect.IO

class AlwaysValidLicenseValidator extends LicenseValidator {
  override def isValid(license: String): IO[Boolean] = IO.pure(true)
}
