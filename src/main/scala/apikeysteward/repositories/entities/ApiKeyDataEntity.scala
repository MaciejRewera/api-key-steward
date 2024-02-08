package apikeysteward.repositories.entities

import apikeysteward.routes.model.admin.CreateApiKeyAdminRequest

import java.time.Instant
import java.util.UUID

object ApiKeyDataEntity {

  case class Read(
      userId: String,
      keyId: UUID,
      name: String,
      description: Option[String] = None,
      scope: List[String] = List.empty,
      createdAt: Instant,
      expiresAt: Instant
  )

  case class Write(
      userId: String,
      keyId: UUID,
      name: String,
      description: Option[String] = None,
      scope: List[String] = List.empty,
      ttl: Int
  )

  object Write {
    def from(createApiKeyAdminRequest: CreateApiKeyAdminRequest, keyId: UUID): ApiKeyDataEntity.Write =
      ApiKeyDataEntity.Write(
        userId = createApiKeyAdminRequest.userId,
        keyId = keyId,
        name = createApiKeyAdminRequest.name,
        description = createApiKeyAdminRequest.description,
        scope = createApiKeyAdminRequest.scope,
        ttl = createApiKeyAdminRequest.ttl
      )
  }
}
