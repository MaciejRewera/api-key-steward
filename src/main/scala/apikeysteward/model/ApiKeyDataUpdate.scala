package apikeysteward.model

import apikeysteward.routes.model.admin.apikey.UpdateApiKeyAdminRequest
import java.util.UUID

case class ApiKeyDataUpdate(
    publicKeyId: UUID,
    name: String,
    description: Option[String] = None,
    userId: String
)

object ApiKeyDataUpdate {

  def from(publicKeyId: UUID, userId: String, updateApiKeyRequest: UpdateApiKeyAdminRequest): ApiKeyDataUpdate =
    ApiKeyDataUpdate(
      publicKeyId = publicKeyId,
      name = updateApiKeyRequest.name,
      description = updateApiKeyRequest.description,
      userId = userId
    )
}
