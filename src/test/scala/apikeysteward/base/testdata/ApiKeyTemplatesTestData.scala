package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.model.ApiKeyTemplate
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.repositories.db.entity.ApiKeyTemplateEntity

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

object ApiKeyTemplatesTestData extends FixedClock {

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

  val apiKeyMaxExpiryPeriod_1: FiniteDuration = Duration(101, TimeUnit.HOURS)
  val apiKeyMaxExpiryPeriod_2: FiniteDuration = Duration(102, TimeUnit.HOURS)
  val apiKeyMaxExpiryPeriod_3: FiniteDuration = Duration(103, TimeUnit.HOURS)
  val apiKeyMaxExpiryPeriodUpdated: FiniteDuration = Duration(201, TimeUnit.HOURS)

  val apiKeyTemplate_1: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_1,
    isDefault = false,
    name = apiKeyTemplateName_1,
    description = apiKeyTemplateDescription_1,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1
  )
  val apiKeyTemplate_2: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_2,
    isDefault = false,
    name = apiKeyTemplateName_2,
    description = apiKeyTemplateDescription_2,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_2
  )
  val apiKeyTemplate_3: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_3,
    isDefault = false,
    name = apiKeyTemplateName_3,
    description = apiKeyTemplateDescription_3,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_3
  )
  val apiKeyTemplateUpdated: ApiKeyTemplate = ApiKeyTemplate(
    publicTemplateId = publicTemplateId_1,
    isDefault = true,
    name = apiKeyTemplateNameUpdated,
    description = apiKeyTemplateDescriptionUpdated,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
  )

  val apiKeyTemplateEntityWrite_1: ApiKeyTemplateEntity.Write = ApiKeyTemplateEntity.Write(
    tenantId = 1L,
    publicTemplateId = publicTemplateIdStr_1,
    isDefault = false,
    name = apiKeyTemplateName_1,
    description = apiKeyTemplateDescription_1,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1
  )
  val apiKeyTemplateEntityRead_1: ApiKeyTemplateEntity.Read = ApiKeyTemplateEntity.Read(
    id = 1L,
    tenantId = 1L,
    publicTemplateId = publicTemplateIdStr_1,
    isDefault = false,
    name = apiKeyTemplateName_1,
    description = apiKeyTemplateDescription_1,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyTemplateEntityWrite_2: ApiKeyTemplateEntity.Write = ApiKeyTemplateEntity.Write(
    tenantId = 2L,
    publicTemplateId = publicTemplateIdStr_2,
    isDefault = false,
    name = apiKeyTemplateName_2,
    description = apiKeyTemplateDescription_2,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_2
  )
  val apiKeyTemplateEntityRead_2: ApiKeyTemplateEntity.Read = ApiKeyTemplateEntity.Read(
    id = 2L,
    tenantId = 2L,
    publicTemplateId = publicTemplateIdStr_2,
    isDefault = false,
    name = apiKeyTemplateName_2,
    description = apiKeyTemplateDescription_2,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val apiKeyTemplateEntityWrite_3: ApiKeyTemplateEntity.Write = ApiKeyTemplateEntity.Write(
    tenantId = 3L,
    publicTemplateId = publicTemplateIdStr_3,
    isDefault = false,
    name = apiKeyTemplateName_3,
    description = apiKeyTemplateDescription_3,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_3
  )
  val apiKeyTemplateEntityRead_3: ApiKeyTemplateEntity.Read = ApiKeyTemplateEntity.Read(
    id = 3L,
    tenantId = 3L,
    publicTemplateId = publicTemplateIdStr_3,
    isDefault = false,
    name = apiKeyTemplateName_3,
    description = apiKeyTemplateDescription_3,
    apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_3,
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