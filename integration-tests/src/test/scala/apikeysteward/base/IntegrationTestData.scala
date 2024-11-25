package apikeysteward.base

import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, publicUserId_3}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity}

import java.util.concurrent.TimeUnit

object IntegrationTestData extends IntegrationTestData

trait IntegrationTestData {

  object ApiKeys extends FixedClock {

    val apiKeyEntityWrite_1: ApiKeyEntity.Write = ApiKeyEntity.Write(apiKey_1.value)
    val apiKeyEntityWrite_2: ApiKeyEntity.Write = ApiKeyEntity.Write(apiKey_2.value)
    val apiKeyEntityWrite_3: ApiKeyEntity.Write = ApiKeyEntity.Write(apiKey_3.value)

    val apiKeyDataEntityWrite_1: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
      apiKeyId = 1L,
      publicKeyId = publicKeyIdStr_1,
      name = "Test API Key name no. 1",
      description = Some("Test key description no. 1"),
      userId = publicUserId_1,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
    )
    val apiKeyDataEntityRead_1: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
      id = 1L,
      apiKeyId = 1L,
      publicKeyId = publicKeyIdStr_1,
      name = "Test API Key name no. 1",
      description = Some("Test key description no. 1"),
      userId = publicUserId_1,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
      createdAt = nowInstant,
      updatedAt = nowInstant
    )

    val apiKeyDataEntityWrite_2: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
      apiKeyId = 2L,
      publicKeyId = publicKeyIdStr_2,
      name = "Test API Key name no. 2",
      description = Some("Test key description no. 2"),
      userId = publicUserId_2,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
    )
    val apiKeyDataEntityRead_2: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
      id = 2L,
      apiKeyId = 2L,
      publicKeyId = publicKeyIdStr_2,
      name = "Test API Key name no. 2",
      description = Some("Test key description no. 2"),
      userId = publicUserId_2,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
      createdAt = nowInstant,
      updatedAt = nowInstant
    )

    val apiKeyDataEntityWrite_3: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
      apiKeyId = 3L,
      publicKeyId = publicKeyIdStr_3,
      name = "Test API Key name no. 3",
      description = Some("Test key description no. 3"),
      userId = publicUserId_3,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
    )
    val apiKeyDataEntityRead_3: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
      id = 3L,
      apiKeyId = 3L,
      publicKeyId = publicKeyIdStr_3,
      name = "Test API Key name no. 3",
      description = Some("Test key description no. 3"),
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

}
