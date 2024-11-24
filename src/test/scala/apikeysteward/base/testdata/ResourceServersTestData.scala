package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.PermissionsTestData.{permission_1, permission_2, permission_3}
import apikeysteward.model.{ResourceServer, ResourceServerUpdate}
import apikeysteward.repositories.db.entity.ResourceServerEntity

import java.util.UUID

object ResourceServersTestData extends FixedClock {

  val publicResourceServerId_1: UUID = UUID.randomUUID()
  val publicResourceServerId_2: UUID = UUID.randomUUID()
  val publicResourceServerId_3: UUID = UUID.randomUUID()
  val publicResourceServerId_4: UUID = UUID.randomUUID()
  val publicResourceServerIdStr_1: String = publicResourceServerId_1.toString
  val publicResourceServerIdStr_2: String = publicResourceServerId_2.toString
  val publicResourceServerIdStr_3: String = publicResourceServerId_3.toString
  val publicResourceServerIdStr_4: String = publicResourceServerId_4.toString

  val resourceServerName_1 = "ResourceServer Name 1"
  val resourceServerName_2 = "ResourceServer Name 2"
  val resourceServerName_3 = "ResourceServer Name 3"
  val resourceServerNameUpdated = "Updated ResourceServer Name"

  val resourceServerDescription_1: Option[String] = Some("Test ResourceServer description no. 1.")
  val resourceServerDescription_2: Option[String] = Some("Test ResourceServer description no. 2.")
  val resourceServerDescription_3: Option[String] = Some("Test ResourceServer description no. 3.")
  val resourceServerDescriptionUpdated: Option[String] = Some("Test Updated ResourceServer description.")

  val resourceServer_1: ResourceServer = ResourceServer(
    resourceServerId = publicResourceServerId_1,
    name = resourceServerName_1,
    description = resourceServerDescription_1,
    isActive = true,
    permissions = List(permission_1)
  )
  val resourceServer_2: ResourceServer = ResourceServer(
    resourceServerId = publicResourceServerId_2,
    name = resourceServerName_2,
    description = resourceServerDescription_2,
    isActive = true,
    permissions = List(permission_2)
  )
  val resourceServer_3: ResourceServer = ResourceServer(
    resourceServerId = publicResourceServerId_3,
    name = resourceServerName_3,
    description = resourceServerDescription_3,
    isActive = true,
    permissions = List(permission_3)
  )

  val resourceServerUpdate_1: ResourceServerUpdate =
    ResourceServerUpdate(
      resourceServerId = publicResourceServerId_1,
      name = resourceServerNameUpdated,
      description = resourceServerDescriptionUpdated
    )

  val resourceServerEntityWrite_1: ResourceServerEntity.Write = ResourceServerEntity.Write(
    tenantId = 1L,
    publicResourceServerId = publicResourceServerIdStr_1,
    name = resourceServerName_1,
    description = resourceServerDescription_1
  )
  val resourceServerEntityRead_1: ResourceServerEntity.Read = ResourceServerEntity.Read(
    id = 1L,
    tenantId = 1L,
    publicResourceServerId = publicResourceServerIdStr_1,
    name = resourceServerName_1,
    description = resourceServerDescription_1,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val resourceServerEntityWrite_2: ResourceServerEntity.Write = ResourceServerEntity.Write(
    tenantId = 2L,
    publicResourceServerId = publicResourceServerIdStr_2,
    name = resourceServerName_2,
    description = resourceServerDescription_2
  )
  val resourceServerEntityRead_2: ResourceServerEntity.Read = ResourceServerEntity.Read(
    id = 2L,
    tenantId = 2L,
    publicResourceServerId = publicResourceServerIdStr_2,
    name = resourceServerName_2,
    description = resourceServerDescription_2,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val resourceServerEntityWrite_3: ResourceServerEntity.Write = ResourceServerEntity.Write(
    tenantId = 3L,
    publicResourceServerId = publicResourceServerIdStr_3,
    name = resourceServerName_3,
    description = resourceServerDescription_3
  )
  val resourceServerEntityRead_3: ResourceServerEntity.Read = ResourceServerEntity.Read(
    id = 3L,
    tenantId = 3L,
    publicResourceServerId = publicResourceServerIdStr_3,
    name = resourceServerName_3,
    description = resourceServerDescription_3,
    createdAt = nowInstant,
    updatedAt = nowInstant,
    deactivatedAt = None
  )

  val resourceServerEntityUpdate_1: ResourceServerEntity.Update = ResourceServerEntity.Update(
    publicResourceServerId = publicResourceServerIdStr_1,
    name = resourceServerNameUpdated,
    description = resourceServerDescriptionUpdated
  )

  val deactivatedResourceServerEntityRead_1: ResourceServerEntity.Read =
    resourceServerEntityRead_1.copy(deactivatedAt = Some(nowInstant))

}
