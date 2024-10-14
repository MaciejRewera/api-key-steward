package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApplicationsTestData.{publicApplicationId_1, _}
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.tenantEntityWrite_1
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionNotFoundError
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.{ApplicationEntity, PermissionEntity}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
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

    val getAllApplications: doobie.ConnectionIO[List[ApplicationEntity.Read]] =
      sql"SELECT * FROM application".query[ApplicationEntity.Read].stream.compile.toList
  }

  "PermissionDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

          res <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
        } yield (res, applicationId)).transact(transactor)

        result.asserting { case (res, expectedApplicationId) =>
          res shouldBe Right(permissionEntityRead_1.copy(id = res.value.id, applicationId = expectedApplicationId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
          res <- Queries.getAllPermissions
        } yield (res, applicationId)).transact(transactor)

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

  "PermissionDb on delete" when {

    "there are no rows in the DB" should {

      "return Left containing PermissionNotFoundError" in {
        permissionDb
          .delete(publicApplicationId_1, publicPermissionId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(PermissionNotFoundError(publicApplicationId_1, publicPermissionId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- permissionDb.delete(publicApplicationId_1, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a row in the DB with a different publicPermissionId" should {

      "return Left containing PermissionNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          res <- permissionDb.delete(publicApplicationId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(PermissionNotFoundError(publicApplicationId_1, publicPermissionId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          _ <- permissionDb.delete(publicApplicationId_1, publicPermissionId_2)
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

    "there is a row in the DB with a different publicApplicationId" should {

      "return Left containing PermissionNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          res <- permissionDb.delete(publicApplicationId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(PermissionNotFoundError(publicApplicationId_2, publicPermissionId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          _ <- permissionDb.delete(publicApplicationId_2, publicPermissionId_1)
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

    "there is a row in the DB with given both publicApplicationId and publicPermissionId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          entityRead <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          res <- permissionDb.delete(publicApplicationId_1, publicPermissionId_1)
        } yield (res, entityRead.value)).transact(transactor)

        result.asserting { case (res, entityRead) =>
          res shouldBe Right(permissionEntityRead_1.copy(id = entityRead.id, applicationId = entityRead.applicationId))
        }
      }

      "delete this row from the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          _ <- permissionDb.delete(publicApplicationId_1, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
      }

      "make NO changes to the application table" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationEntity <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationEntity.id))

          _ <- permissionDb.delete(publicApplicationId_1, publicPermissionId_1)
          res <- Queries.getAllApplications
        } yield (res, applicationEntity)).transact(transactor)

        result.asserting { case (allApplications, applicationEntity) =>
          allApplications.size shouldBe 1

          allApplications shouldBe List(applicationEntity)
        }
      }
    }

    "there are several rows in the DB but only one with given both publicApplicationId and publicPermissionId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          applicationId_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
          entityRead <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId_1))
          _ <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId_1))
          _ <- permissionDb.insert(permissionEntityWrite_3.copy(applicationId = applicationId_2))

          res <- permissionDb.delete(publicApplicationId_1, publicPermissionId_1)
        } yield (res, entityRead.value)).transact(transactor)

        result.asserting { case (res, entityRead) =>
          res shouldBe Right(permissionEntityRead_1.copy(id = entityRead.id, applicationId = entityRead.applicationId))
        }
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
          entityRead_2 <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId))
          entityRead_3 <- permissionDb.insert(permissionEntityWrite_3.copy(applicationId = applicationId))

          _ <- permissionDb.delete(publicApplicationId_1, publicPermissionId_1)
          res <- Queries.getAllPermissions
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allPermissions, entityRead_2, entityRead_3) =>
          allPermissions.size shouldBe 2

          allPermissions should contain theSameElementsAs Seq(entityRead_2, entityRead_3)
        }
      }
    }
  }

  "PermissionDb on getByPublicPermissionId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        permissionDb
          .getByPublicPermissionId(publicApplicationId_1, publicPermissionId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a row in the DB with different publicPermissionId" should {
      "return empty Option" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          res <- permissionDb.getByPublicPermissionId(publicApplicationId_1, publicPermissionId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a row in the DB with different publicApplicationId" should {
      "return empty Option" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          res <- permissionDb.getByPublicPermissionId(publicApplicationId_2, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[PermissionEntity.Read])
      }
    }

    "there is a row in the DB with the same both publicApplicationId and publicPermissionId" should {
      "return this entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

          res <- permissionDb.getByPublicPermissionId(publicApplicationId_1, publicPermissionId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(permissionEntityRead_1.copy(id = res.get.id, applicationId = res.get.applicationId))
        }
      }
    }
  }

  "PermissionDb on getAllBy" when {

    "provided with empty nameFragment" when {

      val nameFragment = Option.empty[String]

      "there are no rows in the DB" should {
        "return empty Stream" in {
          permissionDb
            .getAllBy(publicApplicationId_1)(nameFragment)
            .compile
            .toList
            .transact(transactor)
            .asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with a different applicationId" should {
        "return empty Stream" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

            res <- permissionDb.getAllBy(publicApplicationId_2)(nameFragment).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with the same applicationId" should {
        "return Stream containing this single entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            entityRead <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

            expectedEntities = Seq(entityRead).map(_.value)

            res <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment).compile.toList
          } yield (res, expectedEntities)).transact(transactor)

          result.asserting { case (res, expectedEntities) =>
            res should contain theSameElementsAs expectedEntities
          }
        }
      }

      "there are several rows in the DB" should {
        "return Stream containing only entities with the same applicationId" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            applicationId_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
            entityRead_1 <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId_1))
            _ <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId_2))
            entityRead_3 <- permissionDb.insert(permissionEntityWrite_3.copy(applicationId = applicationId_1))

            expectedEntities = Seq(entityRead_1, entityRead_3).map(_.value)

            res <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment).compile.toList
          } yield (res, expectedEntities)).transact(transactor)

          result.asserting { case (res, expectedEntities) =>
            res should contain theSameElementsAs expectedEntities
          }
        }
      }
    }

    "provided with non-empty nameFragment" when {

      "there are no rows in the DB" should {
        "return empty Stream" in {
          permissionDb
            .getAllBy(publicApplicationId_1)(Some(permissionName_1))
            .compile
            .toList
            .transact(transactor)
            .asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with a different applicationId but matching name" should {
        "return empty Stream" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
            _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))

            res <- permissionDb.getAllBy(publicApplicationId_2)(Some(permissionName_1)).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
        }
      }

      "there is a row in the DB with provided applicationId" when {

        "the row has a different name" should {
          "return empty Stream" in {
            val nameFragment = Option("write")

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              permissionEntity = permissionEntityWrite_1.copy(applicationId = applicationId)
              _ <- permissionDb.insert(permissionEntity)

              res <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment).compile.toList
            } yield res).transact(transactor)

            result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
          }
        }

        "the row has name column exactly the same as provided name" should {
          "return Stream containing this entity" in {
            val nameFragment = Option(permissionName_1)

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              permissionEntity = permissionEntityWrite_1.copy(applicationId = applicationId)
              entityRead <- permissionDb.insert(permissionEntity)

              res <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment).compile.toList
            } yield (res, entityRead.value)).transact(transactor)

            result.asserting { case (res, entityRead) =>
              res shouldBe List(entityRead)
            }
          }
        }

        "the row has name column matching characters of provided name" should {
          "return Stream containing this entity" in {
            val nameFragment_1 = Some("read:")
            val nameFragment_2 = Some(":perm")
            val nameFragment_3 = Some("1")

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              permissionEntity = permissionEntityWrite_1.copy(applicationId = applicationId)
              entityRead <- permissionDb.insert(permissionEntity)

              res_1 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_1).compile.toList
              res_2 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_2).compile.toList
              res_3 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_3).compile.toList
            } yield (res_1, res_2, res_3, entityRead.value)).transact(transactor)

            result.asserting { case (res_1, res_2, res_3, entityRead) =>
              res_1 shouldBe List(entityRead)
              res_2 shouldBe List(entityRead)
              res_3 shouldBe List(entityRead)
            }
          }
        }

        "the row has name column matching characters of provided name, but the capitalisation is different" should {
          "return Stream containing this entity" in {
            val nameFragment_1 = Some("Read:")
            val nameFragment_2 = Some(":PERM")

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              permissionEntity = permissionEntityWrite_1.copy(applicationId = applicationId)
              entityRead <- permissionDb.insert(permissionEntity)

              res_1 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_1).compile.toList
              res_2 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_2).compile.toList
            } yield (res_1, res_2, entityRead.value)).transact(transactor)

            result.asserting { case (res_1, res_2, entityRead) =>
              res_1 shouldBe List(entityRead)
              res_2 shouldBe List(entityRead)
            }
          }
        }
      }

      "there are several rows in the DB with provided applicationId" when {

        "none of them has matching name" should {
          "return empty Stream" in {
            val nameFragment = Option("write")

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              _ <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
              _ <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId))

              res <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment).compile.toList
            } yield res).transact(transactor)

            result.asserting(_ shouldBe List.empty[PermissionEntity.Read])
          }
        }

        "one of them has name column exactly the same as provided name" should {
          "return Stream containing this entity" in {
            val nameFragment = Some(permissionName_1)

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              entityRead_1 <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
              _ <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId))
              _ <- permissionDb.insert(permissionEntityWrite_3.copy(applicationId = applicationId))

              res <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment).compile.toList
            } yield (res, entityRead_1.value)).transact(transactor)

            result.asserting { case (res, entityRead_1) =>
              res shouldBe List(entityRead_1)
            }
          }
        }

        "some of them have name column matching characters of provided name" should {
          "return Stream containing these entities" in {
            val nameFragment_1 = Some("read:")
            val nameFragment_2 = Some(":perm")
            val nameFragment_3 = Some("2")

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              entityRead_1 <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
              entityRead_2 <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId))
              entityRead_3 <- permissionDb.insert(permissionEntityWrite_3.copy(applicationId = applicationId))

              res_1 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_1).compile.toList
              res_2 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_2).compile.toList
              res_3 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_3).compile.toList
            } yield (res_1, res_2, res_3, entityRead_1.value, entityRead_2.value, entityRead_3.value))
              .transact(transactor)

            result.asserting { case (res_1, res_2, res_3, entityRead_1, entityRead_2, entityRead_3) =>
              res_1 should contain theSameElementsAs List(entityRead_1, entityRead_2)
              res_2 should contain theSameElementsAs List(entityRead_1, entityRead_2, entityRead_3)
              res_3 shouldBe List(entityRead_2)
            }
          }
        }

        "some of them have name column matching characters of provided name, but the capitalisation is different" should {
          "return Stream containing these entities" in {
            val nameFragment_1 = Some("Read:")
            val nameFragment_2 = Some(":PERM")
            val nameFragment_3 = Some("RiTe")

            val result = (for {
              tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
              applicationId <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

              entityRead_1 <- permissionDb.insert(permissionEntityWrite_1.copy(applicationId = applicationId))
              entityRead_2 <- permissionDb.insert(permissionEntityWrite_2.copy(applicationId = applicationId))
              entityRead_3 <- permissionDb.insert(permissionEntityWrite_3.copy(applicationId = applicationId))

              res_1 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_1).compile.toList
              res_2 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_2).compile.toList
              res_3 <- permissionDb.getAllBy(publicApplicationId_1)(nameFragment_3).compile.toList
            } yield (res_1, res_2, res_3, entityRead_1.value, entityRead_2.value, entityRead_3.value))
              .transact(transactor)

            result.asserting { case (res_1, res_2, res_3, entityRead_1, entityRead_2, entityRead_3) =>
              res_1 should contain theSameElementsAs List(entityRead_1, entityRead_2)
              res_2 should contain theSameElementsAs List(entityRead_1, entityRead_2, entityRead_3)
              res_3 shouldBe List(entityRead_3)
            }
          }
        }
      }
    }
  }

}
