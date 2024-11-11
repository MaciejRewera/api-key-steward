package apikeysteward.model

import apikeysteward.model.Application.ApplicationId
import apikeysteward.routes.model.admin.application.UpdateApplicationRequest
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}

case class ApplicationUpdate(
    applicationId: ApplicationId,
    name: String,
    description: Option[String]
)

object ApplicationUpdate {
  implicit val encoder: Encoder[ApplicationUpdate] = deriveEncoder[ApplicationUpdate].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[ApplicationUpdate] = deriveDecoder[ApplicationUpdate]

  def from(applicationId: ApplicationId, updateApplicationRequest: UpdateApplicationRequest): ApplicationUpdate =
    ApplicationUpdate(
      applicationId = applicationId,
      name = updateApplicationRequest.name,
      description = updateApplicationRequest.description
    )
}
