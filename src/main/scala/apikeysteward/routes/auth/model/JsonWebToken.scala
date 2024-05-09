package apikeysteward.routes.auth.model

import pdi.jwt.JwtHeader

case class JsonWebToken(
    content: String,
    jwtHeader: JwtHeader,
    jwtClaim: JwtClaimCustom,
    signature: String
)
