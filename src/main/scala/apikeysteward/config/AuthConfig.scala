package apikeysteward.config

case class AuthConfig(
    jwt: JwtConfig,
    jwks: JwksConfig
)
