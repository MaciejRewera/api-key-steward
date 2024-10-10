package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantEntityWrite_1
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.PermissionEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class PermissionDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, application, permission CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val applicationDb = new ApplicationDb
  private val permissionDb = new PermissionDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllPermissions: doobie.ConnectionIO[List[PermissionEntity.Read]] =
      sql"SELECT * FROM permission".query[PermissionEntity.Read].stream.compile.toList
  }

  "PermissionDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

          res <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(permissionEntityRead_1.copy(id = res.value.id, applicationId = res.value.applicationId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting { allPermissions =>
          allPermissions.size shouldBe 1

          val resultPermission = allPermissions.head
          resultPermission shouldBe permissionEntityRead_1.copy(
            id = resultPermission.id,
            applicationId = resultPermission.applicationId
          )
        }
      }
    }

    "there is a row in the DB with a different publicPermissionId" when {

      "the row has the same name, but different applicationId" should {

        val firstEntity = permissionEntityWrite_1
        val secondEntity = permissionEntityWrite_2.copy(name = firstEntity.name)
        val expectedSecondEntity = permissionEntityRead_2.copy(name = firstEntity.name)

        "return inserted entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            applicationId_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
            _ <- permissionDb.insert(firstEntity.copy(applicationId = applicationId_1))

            res <- permissionDb.insert(secondEntity.copy(applicationId = applicationId_2))
          } yield (res, applicationId_2)).transact(transactor)

          result.asserting { case (res, applicationId_2) =>
            res shouldBe Right(expectedSecondEntity.copy(id = res.value.id, applicationId = applicationId_2))
          }
        }

        "insert entity into DB" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            applicationId_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
            entityRead_1 <- permissionDb.insert(firstEntity.copy(applicationId = applicationId_1))

            entityRead_2 <- permissionDb.insert(secondEntity.copy(applicationId = applicationId_2))
            res <- Queries.getAllPermissions
          } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

          result.asserting { case (allPermissions, entityRead_1, entityRead_2) =>
            allPermissions.size shouldBe 2

            val expectedPermissions = Seq(
              permissionEntityRead_1.copy(id = entityRead_1.id, applicationId = entityRead_1.applicationId),
              permissionEntityRead_2.copy(
                id = entityRead_2.id,
                applicationId = entityRead_2.applicationId,
                name = permissionEntityRead_1.name
              )
            )
            allPermissions should contain theSameElementsAs expectedPermissions
          }
        }
      }

      "the row has the same applicationId, but different name" should {

        val firstEntity = permissionEntityWrite_1
        val secondEntity = permissionEntityWrite_2

        "return inserted entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            _ <- permissionDb.insert(firstEntity.copy(applicationId = applicationId))

            res <- permissionDb.insert(secondEntity.copy(applicationId = applicationId))
          } yield (res, applicationId)).transact(transactor)

          result.asserting { case (res, applicationId) =>
            res shouldBe Right(permissionEntityRead_2.copy(id = res.value.id, applicationId = applicationId))
          }
        }

        "insert entity into DB" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            entityRead_1 <- permissionDb.insert(firstEntity.copy(applicationId = applicationId))

            entityRead_2 <- permissionDb.insert(secondEntity.copy(applicationId = applicationId))
            res <- Queries.getAllPermissions
          } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

          result.asserting { case (allPermissions, entityRead_1, entityRead_2) =>
            allPermissions.size shouldBe 2

            val expectedPermissions = Seq(
              permissionEntityRead_1.copy(id = entityRead_1.id, applicationId = entityRead_1.applicationId),
              permissionEntityRead_2.copy(id = entityRead_2.id, applicationId = entityRead_2.applicationId)
            )
            allPermissions should contain theSameElementsAs expectedPermissions
          }
        }
      }

      "the row has the same both name and applicationId" should {

        val firstEntity = permissionEntityWrite_1
        val secondEntity = permissionEntityWrite_2.copy(name = permissionEntityWrite_1.name)

        "return Left containing PermissionAlreadyExistsForThisApplicationError" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            _ <- permissionDb.insert(firstEntity.copy(applicationId = applicationId))

            res <- permissionDb.insert(secondEntity.copy(applicationId = applicationId))
          } yield (res, applicationId)).transact(transactor)

          result.asserting { case (res, applicationId) =>
            res shouldBe Left(PermissionAlreadyExistsForThisApplicationError(permissionName_1, applicationId))
            res.left.value.message shouldBe s"Permission with name = $permissionName_1 already exists for Application with ID = [$applicationId]."
          }
        }

        "NOT insert the second entity into DB" in {
          val result = for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
            applicationId <- applicationDb
              .insert(applicationEntityWrite_1.copy(tenantId = tenantId))
              .map(_.value.id)
              .transact(transactor)

            _ <- permissionDb.insert(firstEntity.copy(applicationId = applicationId)).transact(transactor)

            _ <- permissionDb.insert(secondEntity.copy(applicationId = applicationId)).transact(transactor)
            res <- Queries.getAllPermissions.transact(transactor)
          } yield (res, applicationId)

          result.asserting { case (allPermissions, expectedApplicationId) =>
            allPermissions.size shouldBe 1

            val resultPermission = allPermissions.head
            resultPermission shouldBe permissionEntityRead_1.copy(
              id = resultPermission.id,
              applicationId = expectedApplicationId
            )
          }
        }
      }
    }

    "there is a row in the DB with the same publicPermissionId" should {

      val firstEntity = permissionEntityWrite_1
      val secondEntity = permissionEntityWrite_2.copy(publicPermissionId = firstEntity.publicPermissionId)

      "return Left containing PermissionAlreadyExistsError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          applicationId_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)

          _ <- permissionDb.insert(firstEntity.copy(applicationId = applicationId_1))

          res <- permissionDb.insert(secondEntity.copy(applicationId = applicationId_2))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(PermissionAlreadyExistsError(publicPermissionIdStr_1))
          res.left.value.message shouldBe s"Permission with publicPermissionId = [$publicPermissionIdStr_1] already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          applicationId_1 <- applicationDb
            .insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)
          applicationId_2 <- applicationDb
            .insert(applicationEntityWrite_2.copy(tenantId = tenantId))
            .map(_.value.id)
            .transact(transactor)

          _ <- permissionDb.insert(firstEntity.copy(applicationId = applicationId_1)).transact(transactor)

          _ <- permissionDb.insert(secondEntity.copy(applicationId = applicationId_2)).transact(transactor)
          res <- Queries.getAllPermissions.transact(transactor)
        } yield (res, applicationId_1)

        result.asserting { case (allPermissions, expectedApplicationId) =>
          allPermissions.size shouldBe 1

          val resultPermission = allPermissions.head
          resultPermission shouldBe permissionEntityRead_1.copy(
            id = resultPermission.id,
            applicationId = expectedApplicationId
          )
        }
      }
    }

    "there is no Application with provided applicationId in the DB" should {

      "return Left containing ReferencedApplicationDoesNotExistError" in {
        permissionDb
          .insert(permissionEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedApplicationDoesNotExistError(permissionEntityWrite_1.applicationId)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- permissionDb.insert(permissionEntityWrite_1).transact(transactor)
          res <- Queries.getAllPermissions.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }
    }
  }

}
