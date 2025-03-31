package apikeysteward.base

import apikeysteward.config.JwtConfig
import apikeysteward.routes.auth.model.JwtCustom

trait FixedJwtCustom extends FixedClock {

  val jwtConfigWithoutUserIdFieldName: JwtConfig = JwtConfig(
    allowedIssuers = Set.empty,
    allowedAudiences = Set.empty,
    maxAge = None,
    userIdClaimName = None,
    requireExp = true,
    requireNbf = false,
    requireIat = true,
    requireIss = true,
    requireAud = true
  )

  val jwtCustom = new JwtCustom(fixedClock, jwtConfigWithoutUserIdFieldName)
}
