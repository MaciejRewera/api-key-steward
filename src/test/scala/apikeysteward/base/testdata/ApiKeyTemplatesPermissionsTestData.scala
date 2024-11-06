package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity

object ApiKeyTemplatesPermissionsTestData extends FixedClock {

  val apiKeyTemplatesPermissionsEntityWrite_1: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 11L, permissionId = 12L)
  val apiKeyTemplatesPermissionsEntityRead_1: ApiKeyTemplatesPermissionsEntity.Read =
    ApiKeyTemplatesPermissionsEntity.Read(
      apiKeyTemplateId = 11L,
      permissionId = 12L,
      createdAt = nowInstant,
      updatedAt = nowInstant
    )

  val apiKeyTemplatesPermissionsEntityWrite_2: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 21L, permissionId = 22L)
  val apiKeyTemplatesPermissionsEntityRead_2: ApiKeyTemplatesPermissionsEntity.Read =
    ApiKeyTemplatesPermissionsEntity.Read(
      apiKeyTemplateId = 21L,
      permissionId = 22L,
      createdAt = nowInstant,
      updatedAt = nowInstant
    )

  val apiKeyTemplatesPermissionsEntityWrite_3: ApiKeyTemplatesPermissionsEntity.Write =
    ApiKeyTemplatesPermissionsEntity.Write(apiKeyTemplateId = 31L, permissionId = 32L)
  val apiKeyTemplatesPermissionsEntityRead_3: ApiKeyTemplatesPermissionsEntity.Read =
    ApiKeyTemplatesPermissionsEntity.Read(
      apiKeyTemplateId = 31L,
      permissionId = 32L,
      createdAt = nowInstant,
      updatedAt = nowInstant
    )

}
