package apikeysteward.model

import apikeysteward.model.Application.ApplicationId
import apikeysteward.repositories.db.entity.ApplicationEntity
import apikeysteward.routes.model.admin.application.CreateApplicationRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class Application(
    applicationId: ApplicationId,
    name: String,
    description: Option[String],
    isActive: Boolean
)

object Application {
  implicit val codec: Codec[Application] = deriveCodec[Application]

  type ApplicationId = UUID

  def from(applicationEntity: ApplicationEntity.Read): Application = Application(
    applicationId = UUID.fromString(applicationEntity.publicApplicationId),
    name = applicationEntity.name,
    description = applicationEntity.description,
    isActive = applicationEntity.deactivatedAt.isEmpty
  )

  def from(applicationId: ApplicationId, createApplicationRequest: CreateApplicationRequest): Application = Application(
    applicationId = applicationId,
    name = createApplicationRequest.name,
    description = createApplicationRequest.description,
    isActive = true
  )
}
