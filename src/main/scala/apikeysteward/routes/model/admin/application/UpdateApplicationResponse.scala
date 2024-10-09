package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UpdateApplicationResponse(
    application: Application
)

object UpdateApplicationResponse {
  implicit val codec: Codec[UpdateApplicationResponse] = deriveCodec[UpdateApplicationResponse]
}
