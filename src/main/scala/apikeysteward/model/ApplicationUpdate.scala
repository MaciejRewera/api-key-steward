package apikeysteward.model

import apikeysteward.routes.model.admin.application.UpdateApplicationRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class ApplicationUpdate(
    applicationId: UUID,
    name: String,
    description: Option[String]
)

object ApplicationUpdate {
  implicit val codec: Codec[ApplicationUpdate] = deriveCodec[ApplicationUpdate]

  def from(applicationId: UUID, updateApplicationRequest: UpdateApplicationRequest): ApplicationUpdate =
    ApplicationUpdate(
      applicationId = applicationId,
      name = updateApplicationRequest.name,
      description = updateApplicationRequest.description
    )
}
