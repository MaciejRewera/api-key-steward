package apikeysteward.model

import apikeysteward.model.Tenant.TenantId
import apikeysteward.routes.model.admin.tenant.UpdateTenantRequest
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}

case class TenantUpdate(
    tenantId: TenantId,
    name: String,
    description: Option[String]
)

object TenantUpdate {
  implicit val encoder: Encoder[TenantUpdate] = deriveEncoder[TenantUpdate].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[TenantUpdate] = deriveDecoder[TenantUpdate]

  def from(tenantId: TenantId, updateTenantRequest: UpdateTenantRequest): TenantUpdate =
    TenantUpdate(
      tenantId = tenantId,
      name = updateTenantRequest.name,
      description = updateTenantRequest.description
    )
}
