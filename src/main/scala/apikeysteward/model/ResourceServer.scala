package apikeysteward.model

import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.repositories.db.entity.{PermissionEntity, ResourceServerEntity}
import apikeysteward.routes.model.admin.resourceserver.CreateResourceServerRequest
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class ResourceServer(
    resourceServerId: ResourceServerId,
    name: String,
    description: Option[String],
    permissions: List[Permission]
)

object ResourceServer {
  implicit val encoder: Encoder[ResourceServer] = deriveEncoder[ResourceServer].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[ResourceServer] = deriveDecoder[ResourceServer]

  type ResourceServerId = UUID

  def from(resourceServerEntity: ResourceServerEntity.Read, permissions: List[PermissionEntity.Read]): ResourceServer =
    ResourceServer(
      resourceServerId = UUID.fromString(resourceServerEntity.publicResourceServerId),
      name = resourceServerEntity.name,
      description = resourceServerEntity.description,
      permissions = permissions.map(Permission.from)
    )

  def from(
      resourceServerId: ResourceServerId,
      permissionIds: List[PermissionId],
      createResourceServerRequest: CreateResourceServerRequest
  ): ResourceServer = ResourceServer(
    resourceServerId = resourceServerId,
    name = createResourceServerRequest.name,
    description = createResourceServerRequest.description,
    permissions = permissionIds.zip(createResourceServerRequest.permissions).map { case (id, request) =>
      Permission.from(id, request)
    }
  )

}
