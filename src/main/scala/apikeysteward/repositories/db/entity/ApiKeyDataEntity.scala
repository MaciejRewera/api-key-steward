package apikeysteward.repositories.db.entity

import apikeysteward.model.{ApiKeyData, ApiKeyDataUpdate}

import java.time.Instant
import java.util.UUID

object ApiKeyDataEntity {

  case class Read(
      id: UUID,
      tenantId: UUID,
      apiKeyId: UUID,
      userId: UUID,
      templateId: Option[UUID],
      publicKeyId: String,
      name: String,
      description: Option[String],
      expiresAt: Instant,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      tenantId: UUID,
      apiKeyId: UUID,
      userId: UUID,
      templateId: UUID,
      publicKeyId: String,
      name: String,
      description: Option[String],
      expiresAt: Instant
  )

  object Write {

    def from(
        id: UUID,
        tenantId: UUID,
        apiKeyId: UUID,
        userId: UUID,
        templateId: UUID,
        apiKeyData: ApiKeyData
    ): ApiKeyDataEntity.Write =
      ApiKeyDataEntity.Write(
        id = id,
        tenantId = tenantId,
        apiKeyId = apiKeyId,
        userId = userId,
        templateId = templateId,
        publicKeyId = apiKeyData.publicKeyId.toString,
        name = apiKeyData.name,
        description = apiKeyData.description,
        expiresAt = apiKeyData.expiresAt
      )

  }

  case class Update(
      publicKeyId: String,
      name: String,
      description: Option[String]
  )

  object Update {

    def from(apiKeyDataUpdate: ApiKeyDataUpdate): ApiKeyDataEntity.Update =
      ApiKeyDataEntity.Update(
        publicKeyId = apiKeyDataUpdate.publicKeyId.toString,
        name = apiKeyDataUpdate.name,
        description = apiKeyDataUpdate.description
      )

  }

}
