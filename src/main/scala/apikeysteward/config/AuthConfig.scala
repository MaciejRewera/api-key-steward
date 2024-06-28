package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class AuthConfig(
    allowedIssuers: List[String],
    audience: String,
    maxTokenAge: Option[FiniteDuration],
    requireExp: Boolean,
    requireNbf: Boolean,
    requireIat: Boolean,
    requireIss: Boolean,
    requireAud: Boolean,
    jwks: JwksConfig
)
