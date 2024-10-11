package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.model.Permission
import apikeysteward.repositories.db.entity.PermissionEntity

import java.util.UUID

object PermissionsTestData extends FixedClock {

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

  val permissionDescription_1: Option[String] = Some("Test Permission description no. 1.")
  val permissionDescription_2: Option[String] = Some("Test Permission description no. 2.")
  val permissionDescription_3: Option[String] = Some("Test Permission description no. 3.")

  val permission_1: Permission = Permission(
    permissionId = publicPermissionId_1,
    name = permissionName_1,
    description = permissionDescription_1
  )
  val permission_2: Permission = Permission(
    permissionId = publicPermissionId_2,
    name = permissionName_2,
    description = permissionDescription_2
  )
  val permission_3: Permission = Permission(
    permissionId = publicPermissionId_3,
    name = permissionName_3,
    description = permissionDescription_3
  )

  val permissionEntityWrite_1: PermissionEntity.Write = PermissionEntity.Write(
    applicationId = 1L,
    publicPermissionId = publicPermissionIdStr_1,
    name = permissionName_1,
    description = permissionDescription_1
  )
  val permissionEntityRead_1: PermissionEntity.Read = PermissionEntity.Read(
    id = 1L,
    applicationId = 1L,
    publicPermissionId = publicPermissionIdStr_1,
    name = permissionName_1,
    description = permissionDescription_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val permissionEntityWrite_2: PermissionEntity.Write = PermissionEntity.Write(
    applicationId = 2L,
    publicPermissionId = publicPermissionIdStr_2,
    name = permissionName_2,
    description = permissionDescription_2
  )
  val permissionEntityRead_2: PermissionEntity.Read = PermissionEntity.Read(
    id = 2L,
    applicationId = 2L,
    publicPermissionId = publicPermissionIdStr_2,
    name = permissionName_2,
    description = permissionDescription_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val permissionEntityWrite_3: PermissionEntity.Write = PermissionEntity.Write(
    applicationId = 3L,
    publicPermissionId = publicPermissionIdStr_3,
    name = permissionName_3,
    description = permissionDescription_3
  )
  val permissionEntityRead_3: PermissionEntity.Read = PermissionEntity.Read(
    id = 3L,
    applicationId = 3L,
    publicPermissionId = publicPermissionIdStr_3,
    name = permissionName_3,
    description = permissionDescription_3,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

}
