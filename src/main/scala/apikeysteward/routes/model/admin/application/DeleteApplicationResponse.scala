package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class DeleteApplicationResponse(
    application: Application
)

object DeleteApplicationResponse {
  implicit val codec: Codec[DeleteApplicationResponse] = deriveCodec[DeleteApplicationResponse]
}
