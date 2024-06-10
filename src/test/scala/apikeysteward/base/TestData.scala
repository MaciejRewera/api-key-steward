package apikeysteward.base

import apikeysteward.model.{ApiKey, ApiKeyData, HashedApiKey}

import java.util.UUID
import scala.util.Random

object TestData extends TestData

trait TestData extends FixedClock {

  val apiKeyPrefix: String = "testPrefix_"

  val apiKeyRandomSection_1: String = "test-api-key-1"
  val apiKeyRandomSection_2: String = "test-api-key-2"
  val apiKeyRandomSection_3: String = "test-api-key-3"
  val apiKeyRandomSection_4: String = "test-api-key-4"

  val checksum_1: String = Random.alphanumeric.take(6).mkString
  val checksum_2: String = Random.alphanumeric.take(6).mkString
  val checksum_3: String = Random.alphanumeric.take(6).mkString
  val checksum_4: String = Random.alphanumeric.take(6).mkString

  val apiKey_1: ApiKey = ApiKey(apiKeyPrefix + apiKeyRandomSection_1 + checksum_1)
  val apiKey_2: ApiKey = ApiKey(apiKeyPrefix + apiKeyRandomSection_2 + checksum_2)
  val apiKey_3: ApiKey = ApiKey(apiKeyPrefix + apiKeyRandomSection_3 + checksum_3)
  val apiKey_4: ApiKey = ApiKey(apiKeyPrefix + apiKeyRandomSection_4 + checksum_4)

  val hashedApiKey_1: HashedApiKey = HashedApiKey("test-hashed-api-key-1")
  val hashedApiKey_2: HashedApiKey = HashedApiKey("test-hashed-api-key-2")
  val hashedApiKey_3: HashedApiKey = HashedApiKey("test-hashed-api-key-3")
  val hashedApiKey_4: HashedApiKey = HashedApiKey("test-hashed-api-key-4")

  val publicKeyId_1 = UUID.randomUUID()
  val publicKeyId_2 = UUID.randomUUID()
  val publicKeyId_3 = UUID.randomUUID()
  val name = "Test API Key Name"
  val description = Some("Test key description")
  val userId_1 = "test-user-id-001"
  val userId_2 = "test-user-id-002"
  val userId_3 = "test-user-id-003"
  val ttlSeconds = 60

  val scopeRead_1 = "read:scope-1"
  val scopeRead_2 = "read:scope-2"
  val scopeRead_3 = "read:scope-3"
  val scopeWrite_1 = "write:scope-1"
  val scopeWrite_2 = "write:scope-2"
  val scopeWrite_3 = "write:scope-3"

  val scopes_1 = List(scopeRead_1, scopeWrite_1)
  val scopes_2 = List(scopeRead_2, scopeWrite_2)
  val scopes_3 = List(scopeRead_3, scopeWrite_3)

  val apiKeyData_1: ApiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_1,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds),
    scopes = scopes_1
  )
  val apiKeyData_2: ApiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_2,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds),
    scopes = scopes_2
  )
  val apiKeyData_3: ApiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_3,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = now.plusSeconds(ttlSeconds),
    scopes = scopes_3
  )
}
