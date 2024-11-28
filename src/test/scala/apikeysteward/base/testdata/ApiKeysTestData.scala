package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, publicUserId_3}
import apikeysteward.model._
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Random

object ApiKeysTestData extends FixedClock {

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

  val apiKeyDbId_1: UUID = UUID.randomUUID()
  val apiKeyDbId_2: UUID = UUID.randomUUID()
  val apiKeyDbId_3: UUID = UUID.randomUUID()

  val apiKeyDataDbId_1: UUID = UUID.randomUUID()
  val apiKeyDataDbId_2: UUID = UUID.randomUUID()
  val apiKeyDataDbId_3: UUID = UUID.randomUUID()

  val publicKeyId_1: UUID = UUID.randomUUID()
  val publicKeyId_2: UUID = UUID.randomUUID()
  val publicKeyId_3: UUID = UUID.randomUUID()
  val publicKeyId_4: UUID = UUID.randomUUID()
  val publicKeyIdStr_1: String = publicKeyId_1.toString
  val publicKeyIdStr_2: String = publicKeyId_2.toString
  val publicKeyIdStr_3: String = publicKeyId_3.toString
  val publicKeyIdStr_4: String = publicKeyId_4.toString

  val name_1 = "Test API Key Name no. 1"
  val name_2 = "Test API Key Name no. 2"
  val name_3 = "Test API Key Name no. 3"

  val description_1: Option[String] = Some("Test key description no. 1")
  val description_2: Option[String] = Some("Test key description no. 2")
  val description_3: Option[String] = Some("Test key description no. 3")

  val nameUpdated = "Updated Test APi Key Name"
  val descriptionUpdated: Option[String] = Some("Updated test key description")

  val ttlMinutes = 60

  val apiKeyData_1: ApiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_1,
    name = name_1,
    description = description_1,
    userId = publicUserId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyData_2: ApiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_2,
    name = name_2,
    description = description_2,
    userId = publicUserId_2,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyData_3: ApiKeyData = ApiKeyData(
    publicKeyId = publicKeyId_3,
    name = name_3,
    description = description_3,
    userId = publicUserId_3,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )

  val apiKeyDataUpdate_1: ApiKeyDataUpdate = ApiKeyDataUpdate(
    publicKeyId = publicKeyId_1,
    name = nameUpdated,
    description = descriptionUpdated
  )

  val apiKeyEntityWrite_1: ApiKeyEntity.Write = ApiKeyEntity.Write(id = apiKeyDbId_1, apiKey = apiKey_1.value)
  val apiKeyEntityRead_1: ApiKeyEntity.Read = ApiKeyEntity.Read(
    id = apiKeyDbId_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyEntityWrite_2: ApiKeyEntity.Write = ApiKeyEntity.Write(id = apiKeyDbId_2, apiKey = apiKey_2.value)
  val apiKeyEntityRead_2: ApiKeyEntity.Read = ApiKeyEntity.Read(
    id = apiKeyDbId_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyEntityWrite_3: ApiKeyEntity.Write = ApiKeyEntity.Write(id = apiKeyDbId_3, apiKey = apiKey_3.value)
  val apiKeyEntityRead_3: ApiKeyEntity.Read = ApiKeyEntity.Read(
    id = apiKeyDbId_3,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_1: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
    id = apiKeyDataDbId_1,
    apiKeyId = apiKeyDbId_1,
    publicKeyId = publicKeyIdStr_1,
    name = name_1,
    description = description_1,
    userId = publicUserId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyDataEntityRead_1: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
    id = apiKeyDataDbId_1,
    apiKeyId = apiKeyDbId_1,
    publicKeyId = publicKeyIdStr_1,
    name = name_1,
    description = description_1,
    userId = publicUserId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_2: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
    id = apiKeyDataDbId_2,
    apiKeyId = apiKeyDbId_2,
    publicKeyId = publicKeyIdStr_2,
    name = name_2,
    description = description_2,
    userId = publicUserId_2,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyDataEntityRead_2: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
    id = apiKeyDataDbId_2,
    apiKeyId = apiKeyDbId_2,
    publicKeyId = publicKeyIdStr_2,
    name = name_2,
    description = description_2,
    userId = publicUserId_2,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_3: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
    id = apiKeyDataDbId_3,
    apiKeyId = apiKeyDbId_3,
    publicKeyId = publicKeyIdStr_3,
    name = name_3,
    description = description_3,
    userId = publicUserId_3,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyDataEntityRead_3: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
    id = apiKeyDataDbId_3,
    apiKeyId = apiKeyDbId_3,
    publicKeyId = publicKeyIdStr_3,
    name = name_3,
    description = description_3,
    userId = publicUserId_3,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityUpdate_1: ApiKeyDataEntity.Update = ApiKeyDataEntity.Update(
    publicKeyId = publicKeyIdStr_1,
    name = nameUpdated,
    description = descriptionUpdated
  )
}
