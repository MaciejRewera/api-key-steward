package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class JwtConfig(
    allowedIssuers: Set[String],
    allowedAudiences: Set[String],
    maxAge: Option[FiniteDuration],
    requireExp: Boolean,
    requireNbf: Boolean,
    requireIat: Boolean,
    requireIss: Boolean,
    requireAud: Boolean
)
