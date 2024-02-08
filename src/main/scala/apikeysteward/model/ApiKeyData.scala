package apikeysteward.model

import apikeysteward.repositories.entities.ApiKeyDataEntity
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant
import java.util.UUID

case class ApiKeyData(
    userId: String,
    keyId: UUID,
    name: String,
    description: Option[String] = None,
    scope: List[String] = List.empty,
    createdAt: Instant,
    expiresAt: Instant
)

object ApiKeyData {
  implicit val codec: Codec[ApiKeyData] = deriveCodec[ApiKeyData]

  def from(apiKeyDataEntityRead: ApiKeyDataEntity.Read): ApiKeyData =
    ApiKeyData(
      userId = apiKeyDataEntityRead.userId,
      keyId = apiKeyDataEntityRead.keyId,
      name = apiKeyDataEntityRead.name,
      description = apiKeyDataEntityRead.description,
      scope = apiKeyDataEntityRead.scope,
      createdAt = apiKeyDataEntityRead.createdAt,
      expiresAt = apiKeyDataEntityRead.expiresAt
    )
}
