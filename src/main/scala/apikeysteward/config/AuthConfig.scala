package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class AuthConfig(
    allowedIssuers: List[String],
    audience: String,
    maxTokenAge: Option[FiniteDuration],
    jwks: JwksConfig
)
