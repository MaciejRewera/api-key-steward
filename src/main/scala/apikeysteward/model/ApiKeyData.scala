package apikeysteward.model

import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, PermissionEntity}
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyExpirationCalculator
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.{Clock, Instant}
import java.util.UUID
import java.util.concurrent.TimeUnit

case class ApiKeyData(
    publicKeyId: ApiKeyId,
    name: String,
    description: Option[String],
    publicUserId: UserId,
    expiresAt: Instant,
    permissions: List[Permission]
)

object ApiKeyData {
  implicit val encoder: Encoder[ApiKeyData] = deriveEncoder[ApiKeyData].mapJson(_.deepDropNullValues)
  implicit val decoder: Decoder[ApiKeyData] = deriveDecoder[ApiKeyData]

  type ApiKeyId = UUID

  val ApiKeyTtlResolution: TimeUnit = TimeUnit.SECONDS

  def from(
      publicUserId: UserId,
      apiKeyDataEntityRead: ApiKeyDataEntity.Read,
      permissionEntities: List[PermissionEntity.Read]
  ): ApiKeyData =
    ApiKeyData(
      publicKeyId = UUID.fromString(apiKeyDataEntityRead.publicKeyId),
      name = apiKeyDataEntityRead.name,
      description = apiKeyDataEntityRead.description,
      publicUserId = publicUserId,
      expiresAt = apiKeyDataEntityRead.expiresAt,
      permissions = permissionEntities.map(Permission.from)
    )

  def from(
      publicKeyId: ApiKeyId,
      publicUserId: UserId,
      createApiKeyRequest: CreateApiKeyRequest,
      permissions: List[Permission]
  )(implicit clock: Clock): ApiKeyData =
    ApiKeyData(
      publicKeyId = publicKeyId,
      name = createApiKeyRequest.name,
      description = createApiKeyRequest.description,
      publicUserId = publicUserId,
      expiresAt = ApiKeyExpirationCalculator.calcExpiresAtFromNow(createApiKeyRequest.ttl),
      permissions = permissions
    )
}
