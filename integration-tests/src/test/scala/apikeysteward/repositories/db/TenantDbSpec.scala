package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.IntegrationTestData._
import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError.TenantAlreadyExistsError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantNotDisabledError, TenantNotFoundError}
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.TenantEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class TenantDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllTenants: doobie.ConnectionIO[List[TenantEntity.Read]] =
      sql"SELECT * FROM tenant".query[TenantEntity.Read].stream.compile.toList
  }

  "TenantDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = tenantDb.insert(tenantEntityWrite_1).transact(transactor)

        result.asserting(res => res shouldBe Right(tenantEntityRead_1.copy(id = res.value.id)))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { allTenants =>
          allTenants.size shouldBe 1

          val resultTenant = allTenants.head
          resultTenant shouldBe tenantEntityRead_1.copy(id = resultTenant.id)
        }
      }
    }

    "there is a row in the DB with a different tenantId" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.insert(tenantEntityWrite_2)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(tenantEntityRead_2.copy(id = res.value.id)))
      }

      "insert entity into DB" in {
        val result = (for {
          entityRead_1 <- tenantDb.insert(tenantEntityWrite_1)

          entityRead_2 <- tenantDb.insert(tenantEntityWrite_2)
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allTenants, entityRead_1, entityRead_2) =>
          allTenants.size shouldBe 2

          val expectedTenants = Seq(
            tenantEntityRead_1.copy(id = entityRead_1.id),
            tenantEntityRead_2.copy(id = entityRead_2.id)
          )
          allTenants should contain theSameElementsAs expectedTenants
        }
      }
    }

    "there is a row in the DB with the same tenantId" should {

      "return Left containing TenantInsertionError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.insert(tenantEntityWrite_2.copy(publicTenantId = publicTenantIdStr_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(TenantAlreadyExistsError(publicTenantIdStr_1))
          res.left.value.message shouldBe s"Tenant with publicTenantId = $publicTenantIdStr_1 already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)

          _ <- tenantDb.insert(tenantEntityWrite_2.copy(publicTenantId = publicTenantIdStr_1)).transact(transactor)
          res <- Queries.getAllTenants.transact(transactor)
        } yield res

        result.asserting { allTenants =>
          allTenants.size shouldBe 1

          val resultTenant = allTenants.head
          resultTenant shouldBe tenantEntityRead_1.copy(id = resultTenant.id)
        }
      }
    }
  }

  "TenantDb on update" when {

    val updatedEntityRead = tenantEntityRead_1.copy(name = tenantNameUpdated)

    "there are NO rows in the Tenant table" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .update(tenantEntityUpdate_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.update(tenantEntityUpdate_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is a row in the Tenant table with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.update(tenantEntityUpdate_1.copy(publicTenantId = publicTenantIdStr_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.update(tenantEntityUpdate_1.copy(publicTenantId = publicTenantIdStr_2))
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = tenantEntityRead_1.copy(id = res.head.id)
          res.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the Tenant table with given publicTenantId" should {

      "return updated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.update(tenantEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(updatedEntityRead.copy(id = res.value.id)))
      }

      "update this row" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.update(tenantEntityUpdate_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe updatedEntityRead.copy(id = res.head.id)
        }
      }
    }

    "there are several rows in the Tenant table but only one with given publicTenantId" should {

      "return updated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)

          res <- tenantDb.update(tenantEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(updatedEntityRead.copy(id = res.value.id)))
      }

      "update only this row and leave others unchanged" in {
        val result = (for {
          entityRead_1 <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_2 <- tenantDb.insert(tenantEntityWrite_2)
          entityRead_3 <- tenantDb.insert(tenantEntityWrite_3)

          _ <- tenantDb.update(tenantEntityUpdate_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_1, entityRead_2, entityRead_3) =>
          res.size shouldBe 3

          val expectedEntities = Seq(
            updatedEntityRead.copy(id = entityRead_1.id),
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on getByPublicTenantId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        tenantDb.getByPublicTenantId(publicTenantId_1).transact(transactor).asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.getByPublicTenantId(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe None)
      }
    }

    "there is a row in the DB with the same publicTenantId" should {
      "return this entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.getByPublicTenantId(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Some(tenantEntityRead_1.copy(id = res.get.id)))
      }
    }
  }

  "TenantDb on getAll" when {

    "there are no rows in the DB" should {
      "return empty Stream" in {
        tenantDb.getAll.compile.toList.transact(transactor).asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is a single row in the DB" should {
      "return this single row" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.getAll.compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res shouldBe List(tenantEntityRead_1.copy(id = res.head.id))
        }
      }
    }

    "there are several rows in the DB" should {
      "return all rows" in {
        val result = (for {
          entityRead_1 <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_2 <- tenantDb.insert(tenantEntityWrite_2)
          entityRead_3 <- tenantDb.insert(tenantEntityWrite_3)
          expectedEntities = Seq(entityRead_1, entityRead_2, entityRead_3).map(_.value)

          res <- tenantDb.getAll.compile.toList
        } yield (res, expectedEntities)).transact(transactor)

        result.asserting { case (res, expectedEntities) =>
          res.size shouldBe 3
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on enable" when {

    val enabledEntityRead = tenantEntityRead_1.copy(disabledAt = None)

    "there are NO rows in the Tenant table" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .enable(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.enable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is a disabled row in the Tenant table with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          res <- tenantDb.enable(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          _ <- tenantDb.enable(publicTenantId_2)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = tenantEntityRead_1.copy(id = res.head.id, disabledAt = Some(nowInstant))
          res.head shouldBe expectedEntity
        }
      }
    }

    "there is a disabled row in the Tenant table with given publicTenantId" should {

      "return enabled entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          res <- tenantDb.enable(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(enabledEntityRead.copy(id = res.value.id)))
      }

      "enable this row" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          _ <- tenantDb.enable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe enabledEntityRead.copy(id = res.head.id)
        }
      }
    }

    "there is an enabled row in the Tenant table with given publicTenantId" should {

      "return enabled entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.enable(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(enabledEntityRead.copy(id = res.value.id)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.enable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe enabledEntityRead.copy(id = res.head.id)
        }
      }
    }

    "there are several disabled rows in the Tenant table but only one with given publicTenantId" should {

      "return enabled entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          _ <- tenantDb.disable(publicTenantId_1)
          _ <- tenantDb.disable(publicTenantId_2)
          _ <- tenantDb.disable(publicTenantId_3)

          res <- tenantDb.enable(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(enabledEntityRead.copy(id = res.value.id)))
      }

      "enable only this row and leave others unchanged" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          entityRead_1 <- tenantDb.disable(publicTenantId_1)
          entityRead_2 <- tenantDb.disable(publicTenantId_2)
          entityRead_3 <- tenantDb.disable(publicTenantId_3)

          _ <- tenantDb.enable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_1, entityRead_2, entityRead_3) =>
          res.size shouldBe 3

          val expectedEntities = Seq(
            enabledEntityRead.copy(id = entityRead_1.id),
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on disable" when {

    "there are NO rows in the Tenant table" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .disable(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.disable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is an enabled row in the Tenant table with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.disable(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.disable(publicTenantId_2)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = tenantEntityRead_1.copy(id = res.head.id)
          res.head shouldBe expectedEntity
        }
      }
    }

    "there is an enabled row in the Tenant table with given publicTenantId" should {

      "return disabled entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.disable(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(disabledEntityRead_1.copy(id = res.value.id)))
      }

      "disable this row" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.disable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe disabledEntityRead_1.copy(id = res.head.id)
        }
      }
    }

    "there is a disabled row in the Tenant table with given publicTenantId" should {

      "return disabled entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          res <- tenantDb.disable(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(disabledEntityRead_1.copy(id = res.value.id)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          _ <- tenantDb.disable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe disabledEntityRead_1.copy(id = res.head.id)
        }
      }
    }

    "there are several enabled rows in the Tenant table but only one with given publicTenantId" should {

      "return disabled entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)

          res <- tenantDb.disable(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(disabledEntityRead_1.copy(id = res.value.id)))
      }

      "disable only this row and leave others unchanged" in {
        val result = (for {
          entityRead_1 <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_2 <- tenantDb.insert(tenantEntityWrite_2)
          entityRead_3 <- tenantDb.insert(tenantEntityWrite_3)

          _ <- tenantDb.disable(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_1, entityRead_2, entityRead_3) =>
          res.size shouldBe 3

          val expectedEntities = Seq(
            disabledEntityRead_1.copy(id = entityRead_1.id),
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on deleteDisabled" when {

    "there are no rows in the Tenant table" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .deleteDisabled(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.deleteDisabled(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is a disabled row in the Tenant table with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          res <- tenantDb.deleteDisabled(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          _ <- tenantDb.deleteDisabled(publicTenantId_2)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe List(disabledEntityRead_1.copy(id = res.head.id)))
      }
    }

    "there is an enabled row in the Tenant table with given publicTenantId" should {

      "return Left containing TenantNotDisabledError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.deleteDisabled(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(TenantNotDisabledError(publicTenantId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.deleteDisabled(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe List(tenantEntityRead_1.copy(id = res.head.id)))
      }
    }

    "there is a disabled row in the Tenant table with given publicTenantId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          res <- tenantDb.deleteDisabled(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(disabledEntityRead_1.copy(id = res.value.id)))
      }

      "delete this row from the Tenant table" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.disable(publicTenantId_1)

          _ <- tenantDb.deleteDisabled(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there are several disabled rows in the Tenant table but only one with given publicTenantId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          _ <- tenantDb.disable(publicTenantId_1)
          _ <- tenantDb.disable(publicTenantId_2)
          _ <- tenantDb.disable(publicTenantId_3)

          res <- tenantDb.deleteDisabled(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(disabledEntityRead_1.copy(id = res.value.id)))
      }

      "delete this row from the Tenant table and leave others intact" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          _ <- tenantDb.disable(publicTenantId_1)
          entityRead_2 <- tenantDb.disable(publicTenantId_2)
          entityRead_3 <- tenantDb.disable(publicTenantId_3)

          _ <- tenantDb.deleteDisabled(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_2, entityRead_3) =>
          res.size shouldBe 2

          val expectedEntities = Seq(
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

}
