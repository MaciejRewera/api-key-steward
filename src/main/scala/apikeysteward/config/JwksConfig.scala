package apikeysteward.config

import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

case class JwksConfig(
    url: Uri,
    cacheRefreshPeriod: FiniteDuration,
    supportedAlgorithm: String,
    supportedKeyType: String,
    supportedKeyUse: String
)
