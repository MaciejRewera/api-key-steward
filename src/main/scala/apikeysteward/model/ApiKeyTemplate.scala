package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import apikeysteward.routes.model.CodecCommons
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID
import scala.concurrent.duration.Duration

case class ApiKeyTemplate(
    publicTemplateId: ApiKeyTemplateId,
    isDefault: Boolean,
    name: String,
    description: Option[String],
    apiKeyMaxExpiryPeriod: Duration
)

object ApiKeyTemplate extends CodecCommons {
  implicit val codec: Codec[ApiKeyTemplate] = deriveCodec[ApiKeyTemplate]

  type ApiKeyTemplateId = UUID

  def from(templateEntity: ApiKeyTemplateEntity.Read): ApiKeyTemplate =
    ApiKeyTemplate(
      publicTemplateId = UUID.fromString(templateEntity.publicTemplateId),
      isDefault = templateEntity.isDefault,
      name = templateEntity.name,
      description = templateEntity.description,
      apiKeyMaxExpiryPeriod = templateEntity.apiKeyMaxExpiryPeriod
    )

}
