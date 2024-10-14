package apikeysteward.model

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.repositories.db.entity.{ApplicationEntity, PermissionEntity}
import apikeysteward.routes.model.admin.application.CreateApplicationRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class Application(
    applicationId: ApplicationId,
    name: String,
    description: Option[String],
    isActive: Boolean,
    permissions: List[Permission]
)

object Application {
  implicit val codec: Codec[Application] = deriveCodec[Application]

  type ApplicationId = UUID

  def from(applicationEntity: ApplicationEntity.Read, permissions: List[PermissionEntity.Read]): Application =
    Application(
      applicationId = UUID.fromString(applicationEntity.publicApplicationId),
      name = applicationEntity.name,
      description = applicationEntity.description,
      isActive = applicationEntity.deactivatedAt.isEmpty,
      permissions = permissions.map(Permission.from)
    )

  def from(
      applicationId: ApplicationId,
      permissionIds: List[PermissionId],
      createApplicationRequest: CreateApplicationRequest
  ): Application = Application(
    applicationId = applicationId,
    name = createApplicationRequest.name,
    description = createApplicationRequest.description,
    isActive = true,
    permissions = (permissionIds zip createApplicationRequest.permissions).map { case (id, request) =>
      Permission.from(id, request)
    }
  )
}
