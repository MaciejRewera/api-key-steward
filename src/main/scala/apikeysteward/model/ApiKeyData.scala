package apikeysteward.model

import apikeysteward.repositories.db.entity.ApiKeyDataEntity
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyExpirationCalculator
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}

import java.time.{Clock, Instant}
import java.util.UUID

case class ApiKeyData(
    publicKeyId: UUID,
    name: String,
    description: Option[String],
    userId: String,
    expiresAt: Instant
)

object ApiKeyData {
  implicit val encoder: Encoder[ApiKeyData] = deriveEncoder[ApiKeyData].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[ApiKeyData] = deriveDecoder[ApiKeyData]

  def from(apiKeyDataEntityRead: ApiKeyDataEntity.Read): ApiKeyData =
    ApiKeyData(
      publicKeyId = UUID.fromString(apiKeyDataEntityRead.publicKeyId),
      name = apiKeyDataEntityRead.name,
      description = apiKeyDataEntityRead.description,
      userId = apiKeyDataEntityRead.userId,
      expiresAt = apiKeyDataEntityRead.expiresAt
    )

  def from(publicKeyId: UUID, userId: String, createApiKeyRequest: CreateApiKeyRequest)(
      implicit clock: Clock
  ): ApiKeyData =
    ApiKeyData(
      publicKeyId = publicKeyId,
      name = createApiKeyRequest.name,
      description = createApiKeyRequest.description,
      userId = userId,
      expiresAt = ApiKeyExpirationCalculator.calcExpiresAt(createApiKeyRequest.ttl)
    )
}
