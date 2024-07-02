package apikeysteward.base

import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}

import java.util.UUID

object IntegrationTestData extends IntegrationTestData

trait IntegrationTestData extends FixedClock {

  val ttlSeconds = 60

  val publicKeyId_1 = UUID.randomUUID()
  val publicKeyId_2 = UUID.randomUUID()
  val publicKeyId_3 = UUID.randomUUID()
  val publicKeyIdStr_1 = publicKeyId_1.toString
  val publicKeyIdStr_2 = publicKeyId_2.toString
  val publicKeyIdStr_3 = publicKeyId_3.toString

  val testUserId_1 = "test-user-001"
  val testUserId_2 = "test-user-002"
  val testUserId_3 = "test-user-003"

  val testApiKey_1 = "test-api-key-1"
  val testApiKey_2 = "test-api-key-2"
  val testApiKey_3 = "test-api-key-3"

  val scopeRead_1 = "read:scope-1"
  val scopeRead_2 = "read:scope-2"
  val scopeRead_3 = "read:scope-3"
  val scopeWrite_1 = "write:scope-1"
  val scopeWrite_2 = "write:scope-2"
  val scopeWrite_3 = "write:scope-3"

  val apiKeyEntityWrite_1 = ApiKeyEntity.Write(testApiKey_1)
  val apiKeyEntityWrite_2 = ApiKeyEntity.Write(testApiKey_2)
  val apiKeyEntityWrite_3 = ApiKeyEntity.Write(testApiKey_3)

  val apiKeyDataEntityWrite_1 = ApiKeyDataEntity.Write(
    apiKeyId = 1L,
    publicKeyId = publicKeyIdStr_1,
    name = "Test API Key name no. 1",
    description = Some("Test key description no. 1"),
    userId = testUserId_1,
    expiresAt = nowInstant.plusSeconds(ttlSeconds)
  )
  val apiKeyDataEntityRead_1 = ApiKeyDataEntity.Read(
    id = 1L,
    apiKeyId = 1L,
    publicKeyId = publicKeyIdStr_1,
    name = "Test API Key name no. 1",
    description = Some("Test key description no. 1"),
    userId = testUserId_1,
    expiresAt = nowInstant.plusSeconds(ttlSeconds),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_2 = ApiKeyDataEntity.Write(
    apiKeyId = 2L,
    publicKeyId = publicKeyIdStr_2,
    name = "Test API Key name no. 2",
    description = Some("Test key description no. 2"),
    userId = testUserId_2,
    expiresAt = nowInstant.plusSeconds(ttlSeconds)
  )
  val apiKeyDataEntityRead_2 = ApiKeyDataEntity.Read(
    id = 2L,
    apiKeyId = 2L,
    publicKeyId = publicKeyIdStr_2,
    name = "Test API Key name no. 2",
    description = Some("Test key description no. 2"),
    userId = testUserId_2,
    expiresAt = nowInstant.plusSeconds(ttlSeconds),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_3 = ApiKeyDataEntity.Write(
    apiKeyId = 3L,
    publicKeyId = publicKeyIdStr_3,
    name = "Test API Key name no. 3",
    description = Some("Test key description no. 3"),
    userId = testUserId_3,
    expiresAt = nowInstant.plusSeconds(ttlSeconds)
  )
  val apiKeyDataEntityRead_3 = ApiKeyDataEntity.Read(
    id = 3L,
    apiKeyId = 3L,
    publicKeyId = publicKeyIdStr_3,
    name = "Test API Key name no. 3",
    description = Some("Test key description no. 3"),
    userId = testUserId_3,
    expiresAt = nowInstant.plusSeconds(ttlSeconds),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

}
