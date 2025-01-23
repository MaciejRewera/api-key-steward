package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.PermissionsTestData.{permission_1, permission_2, permission_3, permission_4}
import apikeysteward.base.testdata.TenantsTestData.{tenantDbId_1, tenantDbId_2, tenantDbId_3}
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

object ApiKeyTemplatesTestData extends FixedClock {

  val templateDbId_1: UUID = UUID.randomUUID()
  val templateDbId_2: UUID = UUID.randomUUID()
  val templateDbId_3: UUID = UUID.randomUUID()
  val templateDbId_4: UUID = UUID.randomUUID()

  val publicTemplateId_1: ApiKeyTemplateId = UUID.randomUUID()
  val publicTemplateId_2: ApiKeyTemplateId = UUID.randomUUID()
  val publicTemplateId_3: ApiKeyTemplateId = UUID.randomUUID()
  val publicTemplateId_4: ApiKeyTemplateId = UUID.randomUUID()
  val publicTemplateIdStr_1: String = publicTemplateId_1.toString
  val publicTemplateIdStr_2: String = publicTemplateId_2.toString
  val publicTemplateIdStr_3: String = publicTemplateId_3.toString
  val publicTemplateIdStr_4: String = publicTemplateId_4.toString

  val apiKeyTemplateName_1 = "API Key Template no. 1"
  val apiKeyTemplateName_2 = "API Key Template no. 2"
  val apiKeyTemplateName_3 = "API Key Template no. 3"
  val apiKeyTemplateNameUpdated = "Updated API Key Template Name"

  val apiKeyTemplateDescription_1: Option[String] = Some("Test API Key Template description no. 1.")
  val apiKeyTemplateDescription_2: Option[String] = Some("Test API Key Template description no. 2.")
  val apiKeyTemplateDescription_3: Option[String] = Some("Test API Key Template description no. 3.")
  val apiKeyTemplateDescriptionUpdated: Option[String] = Some("Test Updated API Key Template description.")

  val apiKeyMaxExpiryPeriod_1: FiniteDuration = Duration(101, TimeUnit.MINUTES)
  val apiKeyMaxExpiryPeriod_2: FiniteDuration = Duration(102, TimeUnit.MINUTES)
  val apiKeyMaxExpiryPeriod_3: FiniteDuration = Duration(103, TimeUnit.MINUTES)
  val apiKeyMaxExpiryPeriodUpdated: FiniteDuration = Duration(201, TimeUnit.MINUTES)

  val apiKeyPrefix_1: String = "testPrefix_1_"
  val apiKeyPrefix_2: String = "testPrefix_2_"
  val apiKeyPrefix_3: String = "testPrefix_3_"
  val apiKeyPrefix_4: String = "testPrefix_4_"

  val apiKeyTemplate_1: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_1,
    name = apiKeyTemplateName_1,
    description = apiKeyTemplateDescription_1,
    isDefault = false,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1,
    apiKeyPrefix = apiKeyPrefix_1,
    permissions = List(permission_1)
  )
  val apiKeyTemplate_2: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_2,
    name = apiKeyTemplateName_2,
    description = apiKeyTemplateDescription_2,
    isDefault = false,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_2,
    apiKeyPrefix = apiKeyPrefix_2,
    permissions = List(permission_2)
  )
  val apiKeyTemplate_3: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_3,
    name = apiKeyTemplateName_3,
    description = apiKeyTemplateDescription_3,
    isDefault = false,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_3,
    apiKeyPrefix = apiKeyPrefix_3,
    permissions = List(permission_3)
  )

  val apiKeyTemplateUpdated: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_1,
    name = apiKeyTemplateNameUpdated,
    description = apiKeyTemplateDescriptionUpdated,
    isDefault = true,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated,
    apiKeyPrefix = apiKeyPrefix_4,
    permissions = List(permission_4)
  )

  val apiKeyTemplateEntityWrite_1: ApiKeyTemplateEntity.Write = ApiKeyTemplateEntity.Write(
    id = templateDbId_1,
    tenantId = tenantDbId_1,
    publicTemplateId = publicTemplateIdStr_1,
    isDefault = false,
    name = apiKeyTemplateName_1,
    description = apiKeyTemplateDescription_1,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1,
    apiKeyPrefix = apiKeyPrefix_1
  )
  val apiKeyTemplateEntityRead_1: ApiKeyTemplateEntity.Read = ApiKeyTemplateEntity.Read(
    id = templateDbId_1,
    tenantId = tenantDbId_1,
    publicTemplateId = publicTemplateIdStr_1,
    isDefault = false,
    name = apiKeyTemplateName_1,
    description = apiKeyTemplateDescription_1,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1,
    apiKeyPrefix = apiKeyPrefix_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyTemplateEntityWrite_2: ApiKeyTemplateEntity.Write = ApiKeyTemplateEntity.Write(
    id = templateDbId_2,
    tenantId = tenantDbId_2,
    publicTemplateId = publicTemplateIdStr_2,
    isDefault = false,
    name = apiKeyTemplateName_2,
    description = apiKeyTemplateDescription_2,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_2,
    apiKeyPrefix = apiKeyPrefix_2
  )
  val apiKeyTemplateEntityRead_2: ApiKeyTemplateEntity.Read = ApiKeyTemplateEntity.Read(
    id = templateDbId_2,
    tenantId = tenantDbId_2,
    publicTemplateId = publicTemplateIdStr_2,
    isDefault = false,
    name = apiKeyTemplateName_2,
    description = apiKeyTemplateDescription_2,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_2,
    apiKeyPrefix = apiKeyPrefix_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyTemplateEntityWrite_3: ApiKeyTemplateEntity.Write = ApiKeyTemplateEntity.Write(
    id = templateDbId_3,
    tenantId = tenantDbId_3,
    publicTemplateId = publicTemplateIdStr_3,
    isDefault = false,
    name = apiKeyTemplateName_3,
    description = apiKeyTemplateDescription_3,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_3,
    apiKeyPrefix = apiKeyPrefix_3
  )
  val apiKeyTemplateEntityRead_3: ApiKeyTemplateEntity.Read = ApiKeyTemplateEntity.Read(
    id = templateDbId_3,
    tenantId = tenantDbId_3,
    publicTemplateId = publicTemplateIdStr_3,
    isDefault = false,
    name = apiKeyTemplateName_3,
    description = apiKeyTemplateDescription_3,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_3,
    apiKeyPrefix = apiKeyPrefix_3,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyTemplateEntityUpdate_1: ApiKeyTemplateEntity.Update = ApiKeyTemplateEntity.Update(
    publicTemplateId = publicTemplateIdStr_1,
    isDefault = true,
    name = apiKeyTemplateNameUpdated,
    description = apiKeyTemplateDescriptionUpdated,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
  )

}
