package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ResourceServersTestData.{
  resourceServerDbId_1,
  resourceServerDbId_2,
  resourceServerDbId_3
}
import apikeysteward.base.testdata.TenantsTestData.{tenantDbId_1, tenantDbId_2, tenantDbId_3}
import apikeysteward.model.Permission
import apikeysteward.repositories.db.entity.PermissionEntity
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest

import java.util.UUID

object PermissionsTestData extends FixedClock {

  val permissionDbId_1: UUID = UUID.randomUUID()
  val permissionDbId_2: UUID = UUID.randomUUID()
  val permissionDbId_3: UUID = UUID.randomUUID()

  val publicPermissionId_1: UUID = UUID.randomUUID()
  val publicPermissionId_2: UUID = UUID.randomUUID()
  val publicPermissionId_3: UUID = UUID.randomUUID()
  val publicPermissionId_4: UUID = UUID.randomUUID()
  val publicPermissionIdStr_1: String = publicPermissionId_1.toString
  val publicPermissionIdStr_2: String = publicPermissionId_2.toString
  val publicPermissionIdStr_3: String = publicPermissionId_3.toString
  val publicPermissionIdStr_4: String = publicPermissionId_4.toString

  val permissionName_1 = "read:permission:1"
  val permissionName_2 = "read:permission:2"
  val permissionName_3 = "write:permission:3"
  val permissionName_4 = "write:permission:4"

  val permissionDescription_1: Option[String] = Some("Test Permission description no. 1.")
  val permissionDescription_2: Option[String] = Some("Test Permission description no. 2.")
  val permissionDescription_3: Option[String] = Some("Test Permission description no. 3.")
  val permissionDescription_4: Option[String] = Some("Test Permission description no. 4.")

  val createPermissionRequest_1: CreatePermissionRequest =
    CreatePermissionRequest(name = permissionName_1, description = permissionDescription_1)
  val createPermissionRequest_2: CreatePermissionRequest =
    CreatePermissionRequest(name = permissionName_2, description = permissionDescription_2)
  val createPermissionRequest_3: CreatePermissionRequest =
    CreatePermissionRequest(name = permissionName_3, description = permissionDescription_3)

  val permission_1: Permission = Permission(
    publicPermissionId = publicPermissionId_1,
    name = permissionName_1,
    description = permissionDescription_1
  )
  val permission_2: Permission = Permission(
    publicPermissionId = publicPermissionId_2,
    name = permissionName_2,
    description = permissionDescription_2
  )
  val permission_3: Permission = Permission(
    publicPermissionId = publicPermissionId_3,
    name = permissionName_3,
    description = permissionDescription_3
  )
  val permission_4: Permission = Permission(
    publicPermissionId = publicPermissionId_4,
    name = permissionName_4,
    description = permissionDescription_4
  )

  val permissionEntityWrite_1: PermissionEntity.Write = PermissionEntity.Write(
    id = permissionDbId_1,
    tenantId = tenantDbId_1,
    resourceServerId = resourceServerDbId_1,
    publicPermissionId = publicPermissionIdStr_1,
    name = permissionName_1,
    description = permissionDescription_1
  )
  val permissionEntityRead_1: PermissionEntity.Read = PermissionEntity.Read(
    id = permissionDbId_1,
    tenantId = tenantDbId_1,
    resourceServerId = resourceServerDbId_1,
    publicPermissionId = publicPermissionIdStr_1,
    name = permissionName_1,
    description = permissionDescription_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val permissionEntityWrite_2: PermissionEntity.Write = PermissionEntity.Write(
    id = permissionDbId_2,
    tenantId = tenantDbId_1,
    resourceServerId = resourceServerDbId_1,
    publicPermissionId = publicPermissionIdStr_2,
    name = permissionName_2,
    description = permissionDescription_2
  )
  val permissionEntityRead_2: PermissionEntity.Read = PermissionEntity.Read(
    id = permissionDbId_2,
    tenantId = tenantDbId_1,
    resourceServerId = resourceServerDbId_1,
    publicPermissionId = publicPermissionIdStr_2,
    name = permissionName_2,
    description = permissionDescription_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val permissionEntityWrite_3: PermissionEntity.Write = PermissionEntity.Write(
    id = permissionDbId_3,
    tenantId = tenantDbId_1,
    resourceServerId = resourceServerDbId_1,
    publicPermissionId = publicPermissionIdStr_3,
    name = permissionName_3,
    description = permissionDescription_3
  )
  val permissionEntityRead_3: PermissionEntity.Read = PermissionEntity.Read(
    id = permissionDbId_3,
    tenantId = tenantDbId_1,
    resourceServerId = resourceServerDbId_1,
    publicPermissionId = publicPermissionIdStr_3,
    name = permissionName_3,
    description = permissionDescription_3,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

}
