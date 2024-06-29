package apikeysteward.routes.auth.model

import pdi.jwt.JwtHeader

case class JsonWebToken(
    content: String,
    header: JwtHeader,
    claim: JwtClaimCustom,
    signature: String
)
