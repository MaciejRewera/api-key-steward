package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class CreateApplicationResponse(
    application: Application
)

object CreateApplicationResponse {
  implicit val codec: Codec[CreateApplicationResponse] = deriveCodec[CreateApplicationResponse]
}
