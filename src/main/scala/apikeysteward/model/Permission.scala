package apikeysteward.model

import apikeysteward.model.Permission.PermissionId
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class Permission(
    permissionId: PermissionId,
    name: String,
    description: Option[String]
)

object Permission {
  implicit val codec: Codec[Permission] = deriveCodec[Permission]

  type PermissionId = UUID

  def from(permissionEntity: PermissionEntity.Read): Permission = Permission(
    permissionId = UUID.fromString(permissionEntity.publicPermissionId),
    name = permissionEntity.name,
    description = permissionEntity.description
  )

  def from(permissionId: PermissionId, createPermissionRequest: CreatePermissionRequest): Permission = Permission(
    permissionId = permissionId,
    name = createPermissionRequest.name,
    description = createPermissionRequest.description
  )
}
