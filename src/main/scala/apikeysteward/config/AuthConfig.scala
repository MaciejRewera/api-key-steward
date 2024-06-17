package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class AuthConfig(
    allowedIssuers: String,
    audience: String,
    maxTokenAge: Option[FiniteDuration],
    jwks: JwksConfig
) {
  private val allowedIssuersSeparatorRegex: String = "\\s+"

  lazy val allowedIssuersList: List[String] = allowedIssuers.split(allowedIssuersSeparatorRegex).toList
}
