package apikeysteward.config

import scala.concurrent.duration.FiniteDuration

case class JwksConfig(
    url: String,
    cacheRefreshPeriod: FiniteDuration
)
