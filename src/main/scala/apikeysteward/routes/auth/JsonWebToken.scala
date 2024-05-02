package apikeysteward.routes.auth

import io.circe.Codec
import pdi.jwt.{JwtClaim, JwtHeader}

case class JsonWebToken(
    content: String,
    jwtHeader: JwtHeader,
    jwtClaim: JwtClaim,
    signature: String
)
