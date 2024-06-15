package apikeysteward.config

case class AuthConfig(
    supportedAlgorithm: String,
    supportedKeyType: String,
    supportedKeyUse: String,
    audience: String,
    jwks: JwksConfig
)
