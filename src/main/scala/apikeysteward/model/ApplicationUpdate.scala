package apikeysteward.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class ApplicationUpdate(
    applicationId: UUID,
    name: String,
    description: Option[String]
)

object ApplicationUpdate {
  implicit val codec: Codec[ApplicationUpdate] = deriveCodec[ApplicationUpdate]

}
