package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity
import apikeysteward.routes.model.CodecCommons
import apikeysteward.routes.model.admin.apikeytemplate.{CreateApiKeyTemplateRequest, UpdateApiKeyTemplateRequest}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID
import scala.concurrent.duration.Duration

case class ApiKeyTemplate(publicTemplateId: ApiKeyTemplateId, name: String, description: Option[String], isDefault: Boolean, apiKeyMaxExpiryPeriod: Duration)

object ApiKeyTemplate extends CodecCommons {
  implicit val codec: Codec[ApiKeyTemplate] = deriveCodec[ApiKeyTemplate]

  type ApiKeyTemplateId = UUID

  def from(templateEntity: ApiKeyTemplateEntity.Read): ApiKeyTemplate =
    ApiKeyTemplate(publicTemplateId = UUID.fromString(templateEntity.publicTemplateId), name = templateEntity.name, description = templateEntity.description, isDefault = templateEntity.isDefault, apiKeyMaxExpiryPeriod = templateEntity.apiKeyMaxExpiryPeriod)

  def from(templateId: ApiKeyTemplateId, createApiKeyTemplateRequest: CreateApiKeyTemplateRequest): ApiKeyTemplate =
    ApiKeyTemplate(publicTemplateId = templateId, name = createApiKeyTemplateRequest.name, description = createApiKeyTemplateRequest.description, isDefault = createApiKeyTemplateRequest.isDefault, apiKeyMaxExpiryPeriod = createApiKeyTemplateRequest.apiKeyMaxExpiryPeriod)

  def from(templateId: ApiKeyTemplateId, updateApiKeyTemplateRequest: UpdateApiKeyTemplateRequest): ApiKeyTemplate =
    ApiKeyTemplate(publicTemplateId = templateId, name = updateApiKeyTemplateRequest.name, description = updateApiKeyTemplateRequest.description, isDefault = updateApiKeyTemplateRequest.isDefault, apiKeyMaxExpiryPeriod = updateApiKeyTemplateRequest.apiKeyMaxExpiryPeriod)

}
