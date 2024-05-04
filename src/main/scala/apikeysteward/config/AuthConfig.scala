package apikeysteward.config

case class AuthConfig (
    supportedAlgorithm: String,
    supportedKeyType: String,
    supportedKeyUse: String,
    jwks: JwksConfig
)
