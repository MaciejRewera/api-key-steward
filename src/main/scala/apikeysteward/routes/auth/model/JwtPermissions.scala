package apikeysteward.routes.auth.model

import apikeysteward.routes.auth.JwtValidator.Permission

object JwtPermissions {
  val ReadAdmin: Permission = "read:admin"
  val WriteAdmin: Permission = "write:admin"
}
