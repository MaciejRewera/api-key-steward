package apikeysteward.base

import apikeysteward.base.TestData._
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity, TenantEntity}

import java.util.concurrent.TimeUnit

object IntegrationTestData extends IntegrationTestData

trait IntegrationTestData extends FixedClock {

  val apiKeyEntityWrite_1: ApiKeyEntity.Write = ApiKeyEntity.Write(apiKey_1.value)
  val apiKeyEntityWrite_2: ApiKeyEntity.Write = ApiKeyEntity.Write(apiKey_2.value)
  val apiKeyEntityWrite_3: ApiKeyEntity.Write = ApiKeyEntity.Write(apiKey_3.value)

  val apiKeyDataEntityWrite_1: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
    apiKeyId = 1L,
    publicKeyId = publicKeyIdStr_1,
    name = "Test API Key name no. 1",
    description = Some("Test key description no. 1"),
    userId = userId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyDataEntityRead_1: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
    id = 1L,
    apiKeyId = 1L,
    publicKeyId = publicKeyIdStr_1,
    name = "Test API Key name no. 1",
    description = Some("Test key description no. 1"),
    userId = userId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_2: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
    apiKeyId = 2L,
    publicKeyId = publicKeyIdStr_2,
    name = "Test API Key name no. 2",
    description = Some("Test key description no. 2"),
    userId = userId_2,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyDataEntityRead_2: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
    id = 2L,
    apiKeyId = 2L,
    publicKeyId = publicKeyIdStr_2,
    name = "Test API Key name no. 2",
    description = Some("Test key description no. 2"),
    userId = userId_2,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityWrite_3: ApiKeyDataEntity.Write = ApiKeyDataEntity.Write(
    apiKeyId = 3L,
    publicKeyId = publicKeyIdStr_3,
    name = "Test API Key name no. 3",
    description = Some("Test key description no. 3"),
    userId = userId_3,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
  )
  val apiKeyDataEntityRead_3: ApiKeyDataEntity.Read = ApiKeyDataEntity.Read(
    id = 3L,
    apiKeyId = 3L,
    publicKeyId = publicKeyIdStr_3,
    name = "Test API Key name no. 3",
    description = Some("Test key description no. 3"),
    userId = userId_3,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyDataEntityUpdate_1: ApiKeyDataEntity.Update = ApiKeyDataEntity.Update(
    publicKeyId = publicKeyIdStr_1,
    name = TestData.nameUpdated,
    description = TestData.descriptionUpdated
  )

  val tenantEntityWrite_1: TenantEntity.Write =
    TenantEntity.Write(publicTenantId = publicTenantIdStr_1, name = tenantName_1, description = tenantDescription_1)
  val tenantEntityRead_1: TenantEntity.Read = TenantEntity.Read(
    id = 1L,
    publicTenantId = publicTenantIdStr_1,
    name = tenantName_1,
    description = tenantDescription_1,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val tenantEntityWrite_2: TenantEntity.Write =
    TenantEntity.Write(publicTenantId = publicTenantIdStr_2, name = tenantName_2, description = tenantDescription_2)
  val tenantEntityRead_2: TenantEntity.Read = TenantEntity.Read(
    id = 2L,
    publicTenantId = publicTenantIdStr_2,
    name = tenantName_2,
    description = tenantDescription_2,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val tenantEntityWrite_3: TenantEntity.Write =
    TenantEntity.Write(publicTenantId = publicTenantIdStr_3, name = tenantName_3, description = tenantDescription_3)
  val tenantEntityRead_3: TenantEntity.Read = TenantEntity.Read(
    id = 2L,
    publicTenantId = publicTenantIdStr_3,
    name = tenantName_3,
    description = tenantDescription_3,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val tenantEntityUpdate_1: TenantEntity.Update = TenantEntity.Update(
    publicTenantId = publicTenantIdStr_1,
    name = tenantNameUpdated,
    description = tenantDescriptionUpdated
  )

  val deactivatedEntityRead_1: TenantEntity.Read = tenantEntityRead_1.copy(deactivatedAt = Some(nowInstant))

}
