package apikeysteward.base

import apikeysteward.model._
import apikeysteward.repositories.db.entity.{ApplicationEntity, TenantEntity}

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Random

object TestData extends TestData

trait TestData extends FixedClock {

  object ApiKeys {
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

    val publicKeyId_1: UUID = UUID.randomUUID()
    val publicKeyId_2: UUID = UUID.randomUUID()
    val publicKeyId_3: UUID = UUID.randomUUID()
    val publicKeyId_4: UUID = UUID.randomUUID()
    val publicKeyIdStr_1: String = publicKeyId_1.toString
    val publicKeyIdStr_2: String = publicKeyId_2.toString
    val publicKeyIdStr_3: String = publicKeyId_3.toString
    val publicKeyIdStr_4: String = publicKeyId_4.toString

    val name = "Test API Key Name"
    val nameUpdated = "Updated Test APi Key Name"
    val description: Option[String] = Some("Test key description")
    val descriptionUpdated: Option[String] = Some("Updated test key description")

    val userId_1 = "test-user-id-001"
    val userId_2 = "test-user-id-002"
    val userId_3 = "test-user-id-003"

    val ttlMinutes = 60

    val scopeRead_1 = "read:scope-1"
    val scopeRead_2 = "read:scope-2"
    val scopeRead_3 = "read:scope-3"
    val scopeWrite_1 = "write:scope-1"
    val scopeWrite_2 = "write:scope-2"
    val scopeWrite_3 = "write:scope-3"

    val scopes_1: List[String] = List(scopeRead_1, scopeWrite_1)
    val scopes_2: List[String] = List(scopeRead_2, scopeWrite_2)
    val scopes_3: List[String] = List(scopeRead_3, scopeWrite_3)

    val apiKeyData_1: ApiKeyData = ApiKeyData(
      publicKeyId = publicKeyId_1,
      name = name,
      description = description,
      userId = userId_1,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
      scopes = scopes_1
    )
    val apiKeyData_2: ApiKeyData = ApiKeyData(
      publicKeyId = publicKeyId_2,
      name = name,
      description = description,
      userId = userId_1,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
      scopes = scopes_2
    )
    val apiKeyData_3: ApiKeyData = ApiKeyData(
      publicKeyId = publicKeyId_3,
      name = name,
      description = description,
      userId = userId_1,
      expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
      scopes = scopes_3
    )

    val apiKeyDataUpdate_1: ApiKeyDataUpdate = ApiKeyDataUpdate(
      publicKeyId = publicKeyId_1,
      name = nameUpdated,
      description = descriptionUpdated
    )
  }

  object Tenants {

    val publicTenantId_1: UUID = UUID.randomUUID()
    val publicTenantId_2: UUID = UUID.randomUUID()
    val publicTenantId_3: UUID = UUID.randomUUID()
    val publicTenantId_4: UUID = UUID.randomUUID()
    val publicTenantIdStr_1: String = publicTenantId_1.toString
    val publicTenantIdStr_2: String = publicTenantId_2.toString
    val publicTenantIdStr_3: String = publicTenantId_3.toString
    val publicTenantIdStr_4: String = publicTenantId_4.toString

    val tenantName_1 = "Tenant Name 1"
    val tenantName_2 = "Tenant Name 2"
    val tenantName_3 = "Tenant Name 3"
    val tenantNameUpdated = "Updated Tenant Name"

    val tenantDescription_1: Option[String] = Some("Test Tenant description no. 1.")
    val tenantDescription_2: Option[String] = Some("Test Tenant description no. 2.")
    val tenantDescription_3: Option[String] = Some("Test Tenant description no. 3.")
    val tenantDescriptionUpdated: Option[String] = Some("Test Updated Tenant description.")

    val tenant_1: Tenant =
      Tenant(tenantId = publicTenantId_1, name = tenantName_1, description = tenantDescription_1, isActive = true)
    val tenant_2: Tenant =
      Tenant(tenantId = publicTenantId_2, name = tenantName_2, description = tenantDescription_2, isActive = true)
    val tenant_3: Tenant =
      Tenant(tenantId = publicTenantId_3, name = tenantName_3, description = tenantDescription_3, isActive = true)

    val tenantUpdate_1: TenantUpdate =
      TenantUpdate(tenantId = publicTenantId_1, name = tenantNameUpdated, description = tenantDescriptionUpdated)

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

    val deactivatedTenantEntityRead_1: TenantEntity.Read = tenantEntityRead_1.copy(deactivatedAt = Some(nowInstant))
  }

  object Applications {
    val publicApplicationId_1: UUID = UUID.randomUUID()
    val publicApplicationId_2: UUID = UUID.randomUUID()
    val publicApplicationId_3: UUID = UUID.randomUUID()
    val publicApplicationId_4: UUID = UUID.randomUUID()
    val publicApplicationIdStr_1: String = publicApplicationId_1.toString
    val publicApplicationIdStr_2: String = publicApplicationId_2.toString
    val publicApplicationIdStr_3: String = publicApplicationId_3.toString
    val publicApplicationIdStr_4: String = publicApplicationId_4.toString

    val applicationName_1 = "Application Name 1"
    val applicationName_2 = "Application Name 2"
    val applicationName_3 = "Application Name 3"
    val applicationNameUpdated = "Updated Application Name"

    val applicationDescription_1: Option[String] = Some("Test Application description no. 1.")
    val applicationDescription_2: Option[String] = Some("Test Application description no. 2.")
    val applicationDescription_3: Option[String] = Some("Test Application description no. 3.")
    val applicationDescriptionUpdated: Option[String] = Some("Test Updated Application description.")

    val application_1: Application = Application(
      applicationId = publicApplicationId_1,
      name = applicationName_1,
      description = applicationDescription_1,
      isActive = true
    )
    val application_2: Application = Application(
      applicationId = publicApplicationId_2,
      name = applicationName_2,
      description = applicationDescription_2,
      isActive = true
    )
    val application_3: Application = Application(
      applicationId = publicApplicationId_3,
      name = applicationName_3,
      description = applicationDescription_3,
      isActive = true
    )

    val applicationUpdate_1: ApplicationUpdate =
      ApplicationUpdate(
        applicationId = publicApplicationId_1,
        name = applicationNameUpdated,
        description = applicationDescriptionUpdated
      )

    val applicationEntityWrite_1: ApplicationEntity.Write = ApplicationEntity.Write(
      tenantId = 1L,
      publicApplicationId = publicApplicationIdStr_1,
      name = applicationName_1,
      description = applicationDescription_1
    )
    val applicationEntityRead_1: ApplicationEntity.Read = ApplicationEntity.Read(
      id = 1L,
      tenantId = 1L,
      publicApplicationId = publicApplicationIdStr_1,
      name = applicationName_1,
      description = applicationDescription_1,
      createdAt = nowInstant,
      updatedAt = nowInstant,
      deactivatedAt = None
    )

    val applicationEntityWrite_2: ApplicationEntity.Write = ApplicationEntity.Write(
      tenantId = 2L,
      publicApplicationId = publicApplicationIdStr_2,
      name = applicationName_2,
      description = applicationDescription_2
    )
    val applicationEntityRead_2: ApplicationEntity.Read = ApplicationEntity.Read(
      id = 2L,
      tenantId = 2L,
      publicApplicationId = publicApplicationIdStr_2,
      name = applicationName_2,
      description = applicationDescription_2,
      createdAt = nowInstant,
      updatedAt = nowInstant,
      deactivatedAt = None
    )

    val applicationEntityWrite_3: ApplicationEntity.Write = ApplicationEntity.Write(
      tenantId = 3L,
      publicApplicationId = publicApplicationIdStr_3,
      name = applicationName_3,
      description = applicationDescription_3
    )
    val applicationEntityRead_3: ApplicationEntity.Read = ApplicationEntity.Read(
      id = 3L,
      tenantId = 3L,
      publicApplicationId = publicApplicationIdStr_3,
      name = applicationName_3,
      description = applicationDescription_3,
      createdAt = nowInstant,
      updatedAt = nowInstant,
      deactivatedAt = None
    )

    val applicationEntityUpdate_1: ApplicationEntity.Update = ApplicationEntity.Update(
      publicApplicationId = publicApplicationIdStr_1,
      name = applicationNameUpdated,
      description = applicationDescriptionUpdated
    )

    val deactivatedApplicationEntityRead_1: ApplicationEntity.Read =
      applicationEntityRead_1.copy(deactivatedAt = Some(nowInstant))

  }

}
