package apikeysteward.config

import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

case class JwksConfig(
    urls: Set[Uri],
    fetchRetryAttemptInitialDelay: FiniteDuration,
    fetchRetryMaxAttempts: Int,
    cacheRefreshPeriod: FiniteDuration
)
