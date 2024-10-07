package apikeysteward.routes.model.admin.tenant

import apikeysteward.routes.model.TapirCustomSchemas
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema

case class CreateTenantRequest(
    name: String,
    description: Option[String]
)

object CreateTenantRequest {
  implicit val codec: Codec[CreateTenantRequest] = deriveCodec[CreateTenantRequest]

  implicit val createTenantRequestSchema: Schema[CreateTenantRequest] = TapirCustomSchemas.createTenantRequestSchema
}
