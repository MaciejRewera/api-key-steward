package apikeysteward.license

import cats.effect.IO

trait LicenseValidator {
  def isValid(license: String): IO[Boolean]
}
