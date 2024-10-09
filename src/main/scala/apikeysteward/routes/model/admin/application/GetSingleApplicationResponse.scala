package apikeysteward.routes.model.admin.application

import apikeysteward.model.Application
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetSingleApplicationResponse(
    application: Application
)

object GetSingleApplicationResponse {
  implicit val codec: Codec[GetSingleApplicationResponse] = deriveCodec[GetSingleApplicationResponse]
}
