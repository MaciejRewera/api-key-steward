package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class AuthConfig(
    audience: String,
    maxTokenAge: Option[FiniteDuration],
    jwks: JwksConfig
)
