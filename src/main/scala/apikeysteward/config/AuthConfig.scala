package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class AuthConfig(
    supportedAlgorithm: String,
    supportedKeyType: String,
    supportedKeyUse: String,
    audience: String,
    maxTokenAge: Option[FiniteDuration],
    jwks: JwksConfig
)
