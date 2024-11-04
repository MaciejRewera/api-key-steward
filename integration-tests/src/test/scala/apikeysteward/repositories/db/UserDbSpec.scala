package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError._
import apikeysteward.model.RepositoryErrors.UserDbError.UserNotFoundError
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.UserEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class UserDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, tenant_user CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val userDb = new UserDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllUsers: doobie.ConnectionIO[List[UserEntity.Read]] =
      sql"SELECT * FROM tenant_user".query[UserEntity.Read].stream.compile.toList
  }

  "UserDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          res <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
        } yield (res, tenantId)).transact(transactor)

        result.asserting { case (res, expectedTenantId) =>
          res shouldBe Right(userEntityRead_1.copy(id = res.value.id, tenantId = expectedTenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
          res <- Queries.getAllUsers
        } yield (res, tenantId)).transact(transactor)

        result.asserting { case (allUsers, expectedTenantId) =>
          allUsers.size shouldBe 1

          val resultUser = allUsers.head
          resultUser shouldBe userEntityRead_1.copy(id = resultUser.id, tenantId = expectedTenantId)
        }
      }
    }

    "there is a row in the DB with a different both tenantId and publicUserId" should {

      "return inserted entity" in {
        val result = (for {
          tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))

          res <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_2))
        } yield (res, tenantId_2)).transact(transactor)

        result.asserting { case (res, tenantId_2) =>
          res shouldBe Right(userEntityRead_2.copy(id = res.value.id, tenantId = tenantId_2))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
          entityRead_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))

          entityRead_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_2))
          res <- Queries.getAllUsers
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allUsers, entityRead_1, entityRead_2) =>
          allUsers.size shouldBe 2

          val expectedUsers = Seq(
            userEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            userEntityRead_2.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allUsers should contain theSameElementsAs expectedUsers
        }
      }
    }

    "there is a row in the DB with the same tenantId, but different publicUserId" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId))
        } yield (res, tenantId)).transact(transactor)

        result.asserting { case (res, tenantId) =>
          res shouldBe Right(userEntityRead_2.copy(id = res.value.id, tenantId = tenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          entityRead_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId))
          res <- Queries.getAllUsers
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allUsers, entityRead_1, entityRead_2) =>
          allUsers.size shouldBe 2

          val expectedUsers = Seq(
            userEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            userEntityRead_2.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allUsers should contain theSameElementsAs expectedUsers
        }
      }
    }

    "there is a row in the DB with a different tenantId, but the same publicUserId" should {

      "return inserted entity" in {
        val result = (for {
          tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))

          res <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_2))
        } yield (res, tenantId_2)).transact(transactor)

        result.asserting { case (res, tenantId_2) =>
          res shouldBe Right(userEntityRead_1.copy(id = res.value.id, tenantId = tenantId_2))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
          entityRead_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))

          entityRead_2 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_2))
          res <- Queries.getAllUsers
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allUsers, entityRead_1, entityRead_2) =>
          allUsers.size shouldBe 2

          val expectedUsers = Seq(
            userEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            userEntityRead_1.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allUsers should contain theSameElementsAs expectedUsers
        }
      }
    }

    "there is a row in the DB with the same both tenantId and publicUserId" should {

      "return Left containing UserAlreadyExistsForThisTenantError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
        } yield (res, tenantId)).transact(transactor)

        result.asserting { case (res, tenantId) =>
          res shouldBe Left(UserAlreadyExistsForThisTenantError(publicUserId_1, tenantId))
          res.left.value.message shouldBe s"User with userId = $publicUserId_1 already exists for Tenant with ID = [$tenantId]."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).transact(transactor)

          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).transact(transactor)
          res <- Queries.getAllUsers.transact(transactor)
        } yield (res, tenantId)

        result.asserting { case (allUsers, expectedTenantId) =>
          allUsers.size shouldBe 1

          val resultUser = allUsers.head
          resultUser shouldBe userEntityRead_1.copy(id = resultUser.id, tenantId = expectedTenantId)
        }
      }
    }

    "there is no Tenant with provided tenantId in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        userDb
          .insert(userEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(userEntityWrite_1.tenantId)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- userDb.insert(userEntityWrite_1).transact(transactor)
          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }
  }

  "UserDb on delete" when {

    "there are no rows in the DB" should {

      "return Left containing UserNotFoundError" in {
        userDb
          .delete(publicTenantId_1, publicUserId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_1, publicUserId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- userDb.delete(publicTenantId_1, publicUserId_1)
          res <- Queries.getAllUsers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is a row in the DB with a different both publicTenantId and publicUserId" should {

      "return Left containing UserNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.delete(publicTenantId_2, publicUserId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_2, publicUserId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          _ <- userDb.delete(publicTenantId_2, publicUserId_2)
          res <- Queries.getAllUsers
        } yield res).transact(transactor)

        result.asserting { allUsers =>
          allUsers.size shouldBe 1

          val resultUser = allUsers.head
          resultUser shouldBe userEntityRead_1.copy(id = resultUser.id, tenantId = resultUser.tenantId)
        }
      }
    }

    "there is a row in the DB with the same publicTenantId, but different publicUserId" should {

      "return Left containing UserNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.delete(publicTenantId_1, publicUserId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_1, publicUserId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          _ <- userDb.delete(publicTenantId_1, publicUserId_2)
          res <- Queries.getAllUsers
        } yield res).transact(transactor)

        result.asserting { allUsers =>
          allUsers.size shouldBe 1

          val resultUser = allUsers.head
          resultUser shouldBe userEntityRead_1.copy(id = resultUser.id, tenantId = resultUser.tenantId)
        }
      }
    }

    "there is a row in the DB with a different publicTenantId, but the same publicUserId" should {

      "return Left containing UserNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.delete(publicTenantId_2, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_2, publicUserId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          _ <- userDb.delete(publicTenantId_2, publicUserId_1)
          res <- Queries.getAllUsers
        } yield res).transact(transactor)

        result.asserting { allUsers =>
          allUsers.size shouldBe 1

          val resultUser = allUsers.head
          resultUser shouldBe userEntityRead_1.copy(id = resultUser.id, tenantId = resultUser.tenantId)
        }
      }
    }

    "there is a row in the DB with the same both publicTenantId and publicUserId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.delete(publicTenantId_1, publicUserId_1)
        } yield (res, entityRead.value)).transact(transactor)

        result.asserting { case (res, entityRead) =>
          res shouldBe Right(userEntityRead_1.copy(id = entityRead.id, tenantId = entityRead.tenantId))
        }
      }

      "delete this row from the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          _ <- userDb.delete(publicTenantId_1, publicUserId_1)
          res <- Queries.getAllUsers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there are several rows in the DB but only one with the same both tenantId and publicUserId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
          entityRead <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))
          _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_1))
          _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId_2))

          res <- userDb.delete(publicTenantId_1, publicUserId_1)
        } yield (res, entityRead.value)).transact(transactor)

        result.asserting { case (res, entityRead) =>
          res shouldBe Right(userEntityRead_1.copy(id = entityRead.id, tenantId = entityRead.tenantId))
        }
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))
          entityRead_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_1))
          entityRead_3 <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId_2))

          _ <- userDb.delete(publicTenantId_1, publicUserId_1)
          res <- Queries.getAllUsers
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allUsers, entityRead_2, entityRead_3) =>
          allUsers.size shouldBe 2

          allUsers should contain theSameElementsAs Seq(entityRead_2, entityRead_3)
        }
      }
    }
  }

  "UserDb on getByPublicUserId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        userDb
          .getByPublicUserId(publicTenantId_1, publicUserId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[UserEntity.Read])
      }
    }

    "there is a row in the DB with different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.getByPublicUserId(publicTenantId_2, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[UserEntity.Read])
      }
    }

    "there is a row in the DB with different publicUserId" should {
      "return empty Option" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.getByPublicUserId(publicTenantId_1, publicUserId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[UserEntity.Read])
      }
    }

    "there is a row in the DB with the same both publicTenantId and publicUserId" should {
      "return this entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.getByPublicUserId(publicTenantId_1, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(userEntityRead_1.copy(id = res.get.id, tenantId = res.get.tenantId))
        }
      }
    }
  }

  "UserDb on getAllForTenant" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        userDb
          .getAllForTenant(publicTenantId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is a Tenant in the DB, but there are no Users for this Tenant" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- userDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is a Tenant in the DB with a single User" should {
      "return this single User" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(userEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is a Tenant in the DB with multiple Users" should {
      "return all these Users" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId))
          expectedEntities = Seq(entityRead_1, entityRead_2, entityRead_3).map(_.value)

          res <- userDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield (res, expectedEntities)).transact(transactor)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are several Tenants in the DB with associated Users" when {

      "there are NO Users for given publicTenantId" should {
        "return empty Stream" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId_2))

            res <- userDb.getAllForTenant(publicTenantId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[UserEntity.Read])
        }
      }

      "there is a single User for given publicTenantId" should {
        "return this single User" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            tenantId_3 <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId_3))

            res <- userDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, entityRead_1.value)).transact(transactor)

          result.asserting { case (res, entityRead_1) =>
            res shouldBe List(entityRead_1)
          }
        }
      }

      "there are several Users for given publicTenantId" should {
        "return all these Users" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId_1))
            entityRead_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId_1))
            _ <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId_2))

            expectedEntities = Seq(entityRead_1, entityRead_2).map(_.value)

            res <- userDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, expectedEntities)).transact(transactor)

          result.asserting { case (res, expectedEntities) =>
            res should contain theSameElementsAs expectedEntities
          }
        }
      }
    }
  }
}