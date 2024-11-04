package apikeysteward.repositories.db.entity
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}

import java.time.Instant
import scala.concurrent.duration.Duration

object ApiKeyTemplateEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      publicTemplateId: String,
      name: String,
      description: Option[String],
      isDefault: Boolean,
      apiKeyMaxExpiryPeriod: Duration,
      apiKeyPrefix: String,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      tenantId: Long,
      publicTemplateId: String,
      name: String,
      description: Option[String],
      isDefault: Boolean,
      apiKeyMaxExpiryPeriod: Duration,
      apiKeyPrefix: String
  )

  object Write {
    def from(tenantId: Long, apiKeyTemplate: ApiKeyTemplate): ApiKeyTemplateEntity.Write =
      ApiKeyTemplateEntity.Write(
        tenantId = tenantId,
        publicTemplateId = apiKeyTemplate.publicTemplateId.toString,
        isDefault = apiKeyTemplate.isDefault,
        name = apiKeyTemplate.name,
        description = apiKeyTemplate.description,
        apiKeyMaxExpiryPeriod = apiKeyTemplate.apiKeyMaxExpiryPeriod,
        apiKeyPrefix = apiKeyTemplate.apiKeyPrefix
      )
  }

  case class Update(
      publicTemplateId: String,
      name: String,
      description: Option[String],
      isDefault: Boolean,
      apiKeyMaxExpiryPeriod: Duration
  )

  object Update {
    def from(apiKeyTemplate: ApiKeyTemplateUpdate): ApiKeyTemplateEntity.Update =
      ApiKeyTemplateEntity.Update(
        publicTemplateId = apiKeyTemplate.publicTemplateId.toString,
        isDefault = apiKeyTemplate.isDefault,
        name = apiKeyTemplate.name,
        description = apiKeyTemplate.description,
        apiKeyMaxExpiryPeriod = apiKeyTemplate.apiKeyMaxExpiryPeriod
      )
  }

}
