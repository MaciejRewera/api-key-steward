package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ReactivateApplicationResponse(
    application: Application
)

object ReactivateApplicationResponse {
  implicit val codec: Codec[ReactivateApplicationResponse] = deriveCodec[ReactivateApplicationResponse]
}
