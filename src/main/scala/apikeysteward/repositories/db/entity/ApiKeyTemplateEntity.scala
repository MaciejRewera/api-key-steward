package apikeysteward.repositories.db.entity
import apikeysteward.model.ApiKeyTemplate

import java.time.Instant
import scala.concurrent.duration.Duration

object ApiKeyTemplateEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      publicTemplateId: String,
      isDefault: Boolean,
      name: String,
      description: Option[String],
      apiKeyMaxExpiryPeriod: Duration,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      tenantId: Long,
      publicTemplateId: String,
      isDefault: Boolean,
      name: String,
      description: Option[String],
      apiKeyMaxExpiryPeriod: Duration
  )

  object Write {
    def from(tenantId: Long, apiKeyTemplate: ApiKeyTemplate): ApiKeyTemplateEntity.Write =
      ApiKeyTemplateEntity.Write(
        tenantId = tenantId,
        publicTemplateId = apiKeyTemplate.publicTemplateId.toString,
        isDefault = apiKeyTemplate.isDefault,
        name = apiKeyTemplate.name,
        description = apiKeyTemplate.description,
        apiKeyMaxExpiryPeriod = apiKeyTemplate.apiKeyMaxExpiryPeriod
      )
  }

  case class Update(
      publicTemplateId: String,
      isDefault: Boolean,
      name: String,
      description: Option[String],
      apiKeyMaxExpiryPeriod: Duration
  )

  object Update {
    def from(apiKeyTemplate: ApiKeyTemplate): ApiKeyTemplateEntity.Update =
      ApiKeyTemplateEntity.Update(
        publicTemplateId = apiKeyTemplate.publicTemplateId.toString,
        isDefault = apiKeyTemplate.isDefault,
        name = apiKeyTemplate.name,
        description = apiKeyTemplate.description,
        apiKeyMaxExpiryPeriod = apiKeyTemplate.apiKeyMaxExpiryPeriod
      )
  }

}
