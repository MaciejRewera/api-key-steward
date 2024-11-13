package apikeysteward.model

import apikeysteward.model.Permission.PermissionId
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class Permission(
    publicPermissionId: PermissionId,
    name: String,
    description: Option[String]
)

object Permission {
  implicit val encoder: Encoder[Permission] = deriveEncoder[Permission].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[Permission] = deriveDecoder[Permission]

  type PermissionId = UUID

  def from(permissionEntity: PermissionEntity.Read): Permission = Permission(
    publicPermissionId = UUID.fromString(permissionEntity.publicPermissionId),
    name = permissionEntity.name,
    description = permissionEntity.description
  )

  def from(permissionId: PermissionId, createPermissionRequest: CreatePermissionRequest): Permission = Permission(
    publicPermissionId = permissionId,
    name = createPermissionRequest.name,
    description = createPermissionRequest.description
  )
}
