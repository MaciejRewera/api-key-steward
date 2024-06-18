package apikeysteward.config

import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

case class JwksConfig(
    urls: List[Uri],
    fetchRetryAttemptInitialDelay: FiniteDuration,
    fetchRetryAttemptMaxAmount: Int,
    cacheRefreshPeriod: FiniteDuration,
    supportedAlgorithm: String,
    supportedKeyType: String,
    supportedKeyUse: String
)
