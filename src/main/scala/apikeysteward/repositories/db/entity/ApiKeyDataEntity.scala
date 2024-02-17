package apikeysteward.repositories.db.entity

import apikeysteward.model.ApiKeyData
import apikeysteward.routes.model.admin.CreateApiKeyAdminRequest

import java.time.{Clock, Instant}
import java.util.UUID

object ApiKeyDataEntity {

  case class Read(
      id: Long,
      apiKeyId: Long,
      publicKeyId: String,
      name: String,
      description: Option[String] = None,
      userId: String,
      expiresAt: Instant,
      createdAt: Instant,
      updatedAt: Instant
  )

  case class Write(
      apiKeyId: Long,
      publicKeyId: String,
      name: String,
      description: Option[String] = None,
      userId: String,
      expiresAt: Instant
  )

  object Write {
    def from(apiKeyId: Long, apiKeyData: ApiKeyData): ApiKeyDataEntity.Write =
      ApiKeyDataEntity.Write(
        apiKeyId = apiKeyId,
        publicKeyId = apiKeyData.publicKeyId.toString,
        name = apiKeyData.name,
        description = apiKeyData.description,
        userId = apiKeyData.userId,
        expiresAt = apiKeyData.expiresAt
      )
  }
}
