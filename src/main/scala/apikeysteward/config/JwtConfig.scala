package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class JwtConfig(
    allowedIssuers: List[String],
    allowedAudience: String,
    maxAge: Option[FiniteDuration],
    requireExp: Boolean,
    requireNbf: Boolean,
    requireIat: Boolean,
    requireIss: Boolean,
    requireAud: Boolean
)
