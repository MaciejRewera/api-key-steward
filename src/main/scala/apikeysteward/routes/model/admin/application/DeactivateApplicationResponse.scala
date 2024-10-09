package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeactivateApplicationResponse(
    application: Application
)

object DeactivateApplicationResponse {
  implicit val codec: Codec[DeactivateApplicationResponse] = deriveCodec[DeactivateApplicationResponse]
}
