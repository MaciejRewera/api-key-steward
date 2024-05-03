package apikeysteward.routes.auth

import pdi.jwt.{JwtClaim, JwtHeader}

case class JsonWebToken(
    content: String,
    jwtHeader: JwtHeader,
    jwtClaim: JwtClaim,
    signature: String
)
