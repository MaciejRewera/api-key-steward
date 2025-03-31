package apikeysteward.routes.auth.model

import apikeysteward.routes.auth.JwtAuthorizer.Permission

object JwtPermissions {

  val ReadApiKey: Permission  = "steward:read:apikey"
  val WriteApiKey: Permission = "steward:write:apikey"

  val ReadAdmin: Permission  = "steward:read:admin"
  val WriteAdmin: Permission = "steward:write:admin"
}
