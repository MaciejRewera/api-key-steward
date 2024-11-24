package apikeysteward.model

import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.routes.model.admin.resourceserver.UpdateResourceServerRequest
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ResourceServerUpdate(
    resourceServerId: ResourceServerId,
    name: String,
    description: Option[String]
)

object ResourceServerUpdate {
  implicit val encoder: Encoder[ResourceServerUpdate] =
    deriveEncoder[ResourceServerUpdate].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[ResourceServerUpdate] = deriveDecoder[ResourceServerUpdate]

  def from(
      resourceServerId: ResourceServerId,
      updateResourceServerRequest: UpdateResourceServerRequest
  ): ResourceServerUpdate =
    ResourceServerUpdate(
      resourceServerId = resourceServerId,
      name = updateResourceServerRequest.name,
      description = updateResourceServerRequest.description
    )
}
