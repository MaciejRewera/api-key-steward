package apikeysteward.routes.auth.model

import apikeysteward.routes.auth.JwtValidator.Permission

object JwtPermissions {

  val ReadApiKey: Permission = "read:apikey"
  val WriteApiKey: Permission = "write:apikey"

  val ReadAdmin: Permission = "read:admin"
  val WriteAdmin: Permission = "write:admin"
}
