package apikeysteward.config

case class Auth0ApiConfig(
    domain: String,
    audience: String,
    clientId: String,
    clientSecret: String
)
