package apikeysteward.routes.model.admin

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class GetMultipleUserIdsResponse(
    userIds: List[String]
)

object GetMultipleUserIdsResponse {
  implicit val codec: Codec[GetMultipleUserIdsResponse] = deriveCodec[GetMultipleUserIdsResponse]
}
