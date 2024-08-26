package apikeysteward.model

import java.util.UUID

case class Tenant(
    publicId: UUID,
    name: String
)
