package apikeysteward.model

import apikeysteward.model.Application.ApplicationId
import apikeysteward.routes.model.admin.application.UpdateApplicationRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ApplicationUpdate(
    applicationId: ApplicationId,
    name: String,
    description: Option[String]
)

object ApplicationUpdate {
  implicit val codec: Codec[ApplicationUpdate] = deriveCodec[ApplicationUpdate]

  def from(applicationId: ApplicationId, updateApplicationRequest: UpdateApplicationRequest): ApplicationUpdate =
    ApplicationUpdate(
      applicationId = applicationId,
      name = updateApplicationRequest.name,
      description = updateApplicationRequest.description
    )
}
