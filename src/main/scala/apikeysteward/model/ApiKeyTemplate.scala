package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import apikeysteward.routes.model.CodecCommons
import apikeysteward.routes.model.admin.apikeytemplate.{CreateApiKeyTemplateRequest, UpdateApiKeyTemplateRequest}
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

  def from(templateId: ApiKeyTemplateId, createApiKeyTemplateRequest: CreateApiKeyTemplateRequest): ApiKeyTemplate =
    ApiKeyTemplate(
      publicTemplateId = templateId,
      isDefault = createApiKeyTemplateRequest.isDefault,
      name = createApiKeyTemplateRequest.name,
      description = createApiKeyTemplateRequest.description,
      apiKeyMaxExpiryPeriod = createApiKeyTemplateRequest.apiKeyMaxExpiryPeriod
    )

  def from(templateId: ApiKeyTemplateId, updateApiKeyTemplateRequest: UpdateApiKeyTemplateRequest): ApiKeyTemplate =
    ApiKeyTemplate(
      publicTemplateId = templateId,
      isDefault = updateApiKeyTemplateRequest.isDefault,
      name = updateApiKeyTemplateRequest.name,
      description = updateApiKeyTemplateRequest.description,
      apiKeyMaxExpiryPeriod = updateApiKeyTemplateRequest.apiKeyMaxExpiryPeriod
    )

}
