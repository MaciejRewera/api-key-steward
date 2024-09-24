package apikeysteward.model

import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ScopeEntity}
import apikeysteward.routes.model.CreateApiKeyRequest
import apikeysteward.routes.model.admin.UpdateApiKeyRequest
import apikeysteward.services.ApiKeyExpirationCalculator
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.{Clock, Instant}
import java.util.UUID

case class ApiKeyData(
    publicKeyId: UUID,
    name: String,
    description: Option[String] = None,
    userId: String,
    expiresAt: Instant,
    scopes: List[String]
)

object ApiKeyData {
  implicit val codec: Codec[ApiKeyData] = deriveCodec[ApiKeyData]

  def from(apiKeyDataEntityRead: ApiKeyDataEntity.Read, scopeEntitiesRead: List[ScopeEntity.Read]): ApiKeyData =
    ApiKeyData(
      publicKeyId = UUID.fromString(apiKeyDataEntityRead.publicKeyId),
      name = apiKeyDataEntityRead.name,
      description = apiKeyDataEntityRead.description,
      userId = apiKeyDataEntityRead.userId,
      expiresAt = apiKeyDataEntityRead.expiresAt,
      scopes = scopeEntitiesRead.map(_.scope)
    )

  def from(publicKeyId: UUID, userId: String, createApiKeyRequest: CreateApiKeyRequest)(
      implicit clock: Clock
  ): ApiKeyData =
    ApiKeyData(
      publicKeyId = publicKeyId,
      name = createApiKeyRequest.name,
      description = createApiKeyRequest.description,
      userId = userId,
      expiresAt = ApiKeyExpirationCalculator.calcExpiresAt(createApiKeyRequest.ttl),
      scopes = createApiKeyRequest.scopes
    )

  def from(publicKeyId: UUID, userId: String, updateApiKeyRequest: UpdateApiKeyRequest)(
      implicit clock: Clock
  ): ApiKeyData =
    ApiKeyData(
      publicKeyId = publicKeyId,
      name = updateApiKeyRequest.name,
      description = updateApiKeyRequest.description,
      userId = userId,
      expiresAt = ApiKeyExpirationCalculator.calcExpiresAt(updateApiKeyRequest.ttl),
      scopes = List.empty
    )
}
