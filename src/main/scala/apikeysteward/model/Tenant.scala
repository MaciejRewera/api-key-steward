package apikeysteward.model

import apikeysteward.repositories.db.entity.TenantEntity
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class Tenant(
    tenantId: UUID,
    name: String,
    enabled: Boolean
)

object Tenant {
  implicit val codec: Codec[Tenant] = deriveCodec[Tenant]

  def from(tenantEntityRead: TenantEntity.Read): Tenant = Tenant(
    tenantId = UUID.fromString(tenantEntityRead.publicTenantId),
    name = tenantEntityRead.name,
    enabled = tenantEntityRead.disabledAt.isEmpty
  )
}
