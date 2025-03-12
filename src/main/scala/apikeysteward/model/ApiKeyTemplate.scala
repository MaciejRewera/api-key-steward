package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, PermissionEntity}
import apikeysteward.routes.model.CodecCommons
import apikeysteward.routes.model.admin.apikeytemplate.CreateApiKeyTemplateRequest
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.concurrent.duration.Duration

case class ApiKeyTemplate(
    publicTemplateId: ApiKeyTemplateId,
    name: String,
    description: Option[String],
    isDefault: Boolean,
    apiKeyMaxExpiryPeriod: Duration,
    apiKeyPrefix: String,
    randomSectionLength: Int,
    permissions: List[Permission]
)

object ApiKeyTemplate extends CodecCommons {
  implicit val encoder: Encoder[ApiKeyTemplate] = deriveEncoder[ApiKeyTemplate].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[ApiKeyTemplate] = deriveDecoder[ApiKeyTemplate]

  type ApiKeyTemplateId = UUID

  def from(templateEntity: ApiKeyTemplateEntity.Read, permissionEntities: List[PermissionEntity.Read]): ApiKeyTemplate =
    ApiKeyTemplate(
      publicTemplateId = UUID.fromString(templateEntity.publicTemplateId),
      name = templateEntity.name,
      description = templateEntity.description,
      isDefault = templateEntity.isDefault,
      apiKeyMaxExpiryPeriod = templateEntity.apiKeyMaxExpiryPeriod,
      apiKeyPrefix = templateEntity.apiKeyPrefix,
      randomSectionLength = templateEntity.randomSectionLength,
      permissions = permissionEntities.map(Permission.from)
    )

  def from(templateId: ApiKeyTemplateId, createApiKeyTemplateRequest: CreateApiKeyTemplateRequest): ApiKeyTemplate =
    ApiKeyTemplate(
      publicTemplateId = templateId,
      name = createApiKeyTemplateRequest.name,
      description = createApiKeyTemplateRequest.description,
      isDefault = createApiKeyTemplateRequest.isDefault,
      apiKeyMaxExpiryPeriod = createApiKeyTemplateRequest.apiKeyMaxExpiryPeriod,
      apiKeyPrefix = createApiKeyTemplateRequest.apiKeyPrefix,
      randomSectionLength = createApiKeyTemplateRequest.randomSectionLength,
      permissions = List.empty
    )

}
