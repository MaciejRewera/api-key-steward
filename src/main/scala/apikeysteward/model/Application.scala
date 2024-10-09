package apikeysteward.model

import apikeysteward.repositories.db.entity.ApplicationEntity
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

case class Application(
    applicationId: UUID,
    name: String,
    description: Option[String],
    isActive: Boolean
)

object Application {
  implicit val codec: Codec[Application] = deriveCodec[Application]

  def from(applicationEntity: ApplicationEntity.Read): Application = Application(
    applicationId = UUID.fromString(applicationEntity.publicApplicationId),
    name = applicationEntity.name,
    description = applicationEntity.description,
    isActive = applicationEntity.deactivatedAt.isEmpty
  )
}
