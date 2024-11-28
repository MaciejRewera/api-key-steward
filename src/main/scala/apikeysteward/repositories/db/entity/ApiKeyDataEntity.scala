package apikeysteward.repositories.db.entity

import apikeysteward.model.{ApiKeyData, ApiKeyDataUpdate}

import java.time.Instant
import java.util.UUID

object ApiKeyDataEntity {

  case class Read(
      id: UUID,
      apiKeyId: UUID,
      publicKeyId: String,
      name: String,
      description: Option[String],
      userId: String,
      expiresAt: Instant,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      apiKeyId: UUID,
      publicKeyId: String,
      name: String,
      description: Option[String],
      userId: String,
      expiresAt: Instant
  )

  object Write {
    def from(id: UUID, apiKeyId: UUID, apiKeyData: ApiKeyData): ApiKeyDataEntity.Write =
      ApiKeyDataEntity.Write(
        id = id,
        apiKeyId = apiKeyId,
        publicKeyId = apiKeyData.publicKeyId.toString,
        name = apiKeyData.name,
        description = apiKeyData.description,
        userId = apiKeyData.userId,
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
