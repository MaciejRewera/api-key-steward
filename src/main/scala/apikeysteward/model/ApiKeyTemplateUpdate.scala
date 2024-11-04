package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.routes.model.CodecCommons
import apikeysteward.routes.model.admin.apikeytemplate.UpdateApiKeyTemplateRequest
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import scala.concurrent.duration.Duration

case class ApiKeyTemplateUpdate(
    publicTemplateId: ApiKeyTemplateId,
    name: String,
    description: Option[String],
    isDefault: Boolean,
    apiKeyMaxExpiryPeriod: Duration
)

object ApiKeyTemplateUpdate extends CodecCommons {
  implicit val codec: Codec[ApiKeyTemplateUpdate] = deriveCodec[ApiKeyTemplateUpdate]

  def from(
      templateId: ApiKeyTemplateId,
      updateApiKeyTemplateRequest: UpdateApiKeyTemplateRequest
  ): ApiKeyTemplateUpdate =
    ApiKeyTemplateUpdate(
      publicTemplateId = templateId,
      name = updateApiKeyTemplateRequest.name,
      description = updateApiKeyTemplateRequest.description,
      isDefault = updateApiKeyTemplateRequest.isDefault,
      apiKeyMaxExpiryPeriod = updateApiKeyTemplateRequest.apiKeyMaxExpiryPeriod
    )

}
