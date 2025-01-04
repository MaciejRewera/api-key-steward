package apikeysteward.repositories.db.entity

import java.util.UUID

object ApiKeysPermissionsEntity {

  case class Read(
      tenantId: UUID,
      apiKeyDataId: UUID,
      permissionId: UUID
  )

  case class Write(
      tenantId: UUID,
      apiKeyDataId: UUID,
      permissionId: UUID
  )

}
