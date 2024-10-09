package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.model.{Application, ApplicationUpdate}
import apikeysteward.repositories.db.entity.ApplicationEntity

import java.util.UUID

object ApplicationsTestData extends FixedClock {

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
