package apikeysteward.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Pagination(
    page: Int = 1,
    itemsPerPage: Int = 50
)

object Pagination {
  implicit val codec: Codec[Pagination] = deriveCodec[Pagination]
}
