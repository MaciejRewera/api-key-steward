package apikeysteward.repositories.db.entity

import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db.DoobieCustomMeta
import doobie.postgres._
import doobie.postgres.implicits._

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.Duration

object ApiKeyTemplateEntity extends DoobieCustomMeta {

  case class Read(
      id: UUID,
      tenantId: UUID,
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
      id: UUID,
      tenantId: UUID,
      publicTemplateId: String,
      name: String,
      description: Option[String],
      isDefault: Boolean,
      apiKeyMaxExpiryPeriod: Duration,
      apiKeyPrefix: String
  )

  object Write {
    def from(id: UUID, tenantId: UUID, apiKeyTemplate: ApiKeyTemplate): ApiKeyTemplateEntity.Write =
      ApiKeyTemplateEntity.Write(
        id = id,
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
