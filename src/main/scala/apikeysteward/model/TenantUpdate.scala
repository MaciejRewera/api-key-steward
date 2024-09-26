package apikeysteward.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class TenantUpdate(
    tenantId: UUID,
    name: String
)

object TenantUpdate {
  implicit val codec: Codec[TenantUpdate] = deriveCodec[TenantUpdate]
}
