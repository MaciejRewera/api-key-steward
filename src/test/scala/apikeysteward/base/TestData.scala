package apikeysteward.base

import apikeysteward.model.ApiKeyData

import java.util.UUID

object TestData extends TestData

trait TestData extends FixedClock {

  val apiKey_1 = "test-api-key-1"
  val apiKey_2 = "test-api-key-2"

  val publicKeyId_1 = UUID.randomUUID()
  val publicKeyId_2 = UUID.randomUUID()
  val publicKeyId_3 = UUID.randomUUID()
  val name = "Test API Key Name"
  val description = Some("Test key description")
  val userId_1 = "test-user-id-001"
  val userId_2 = "test-user-id-002"
  val userId_3 = "test-user-id-003"
  val ttlSeconds = 60

  val apiKeyData_1 = ApiKeyData(
    publicKeyId = publicKeyId_1,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
  val apiKeyData_2 = ApiKeyData(
    publicKeyId = publicKeyId_2,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
  val apiKeyData_3 = ApiKeyData(
    publicKeyId = publicKeyId_3,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds)
  )
}
