package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData.Tenants._
import apikeysteward.model.RepositoryErrors.TenantDbError.TenantInsertionError.TenantAlreadyExistsError
import apikeysteward.model.RepositoryErrors.TenantDbError.{TenantIsNotDeactivatedError, TenantNotFoundError}
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.TenantEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
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

      "insert entity with empty description into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1.copy(description = None))

          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { allTenants =>
          allTenants.size shouldBe 1

          val resultTenant = allTenants.head
          resultTenant shouldBe tenantEntityRead_1.copy(id = resultTenant.id, description = None)
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

      "insert entity with empty description into DB" in {
        val result = (for {
          entityRead_1 <- tenantDb.insert(tenantEntityWrite_1)

          entityRead_2 <- tenantDb.insert(tenantEntityWrite_2.copy(description = None))
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allTenants, entityRead_1, entityRead_2) =>
          allTenants.size shouldBe 2

          val expectedTenants = Seq(
            tenantEntityRead_1.copy(id = entityRead_1.id),
            tenantEntityRead_2.copy(id = entityRead_2.id, description = None)
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

    val updatedEntityRead = tenantEntityRead_1.copy(name = tenantNameUpdated, description = tenantDescriptionUpdated)

    "there are NO rows in the DB" should {

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

    "there is a row in the DB with different publicTenantId" should {

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

    "there is a row in the DB with given publicTenantId" should {

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

    "there are several rows in the DB but only one with given publicTenantId" should {

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

  "TenantDb on activate" when {

    val activatedEntityRead = tenantEntityRead_1.copy(deactivatedAt = None)

    "there are NO rows in the DB" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .activate(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.activate(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is a deactivated row in the DB with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.deactivate(publicTenantId_1)

          res <- tenantDb.activate(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.deactivate(publicTenantId_1)

          _ <- tenantDb.activate(publicTenantId_2)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = deactivatedTenantEntityRead_1.copy(id = res.head.id)
          res.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicTenantId" when {

      "the row is deactivated" should {

        "return activated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- tenantDb.deactivate(publicTenantId_1)

            res <- tenantDb.activate(publicTenantId_1)
          } yield res).transact(transactor)

          result.asserting(res => res shouldBe Right(activatedEntityRead.copy(id = res.value.id)))
        }

        "activate this row" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- tenantDb.deactivate(publicTenantId_1)

            _ <- tenantDb.activate(publicTenantId_1)
            res <- Queries.getAllTenants
          } yield res).transact(transactor)

          result.asserting { res =>
            res.size shouldBe 1
            res.head shouldBe activatedEntityRead.copy(id = res.head.id)
          }
        }
      }

      "the row is activated" should {

        "return activated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)

            res <- tenantDb.activate(publicTenantId_1)
          } yield res).transact(transactor)

          result.asserting(res => res shouldBe Right(activatedEntityRead.copy(id = res.value.id)))
        }

        "make NO changes to the DB" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)

            _ <- tenantDb.activate(publicTenantId_1)
            res <- Queries.getAllTenants
          } yield res).transact(transactor)

          result.asserting { res =>
            res.size shouldBe 1
            res.head shouldBe activatedEntityRead.copy(id = res.head.id)
          }
        }
      }
    }

    "there are several deactivated rows in the DB but only one with given publicTenantId" should {

      "return activated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          _ <- tenantDb.deactivate(publicTenantId_1)
          _ <- tenantDb.deactivate(publicTenantId_2)
          _ <- tenantDb.deactivate(publicTenantId_3)

          res <- tenantDb.activate(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(activatedEntityRead.copy(id = res.value.id)))
      }

      "activate only this row and leave others unchanged" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          entityRead_1 <- tenantDb.deactivate(publicTenantId_1)
          entityRead_2 <- tenantDb.deactivate(publicTenantId_2)
          entityRead_3 <- tenantDb.deactivate(publicTenantId_3)

          _ <- tenantDb.activate(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_1, entityRead_2, entityRead_3) =>
          res.size shouldBe 3

          val expectedEntities = Seq(
            activatedEntityRead.copy(id = entityRead_1.id),
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on deactivate" when {

    "there are NO rows in the DB" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .deactivate(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.deactivate(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is an activated row in the DB with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.deactivate(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.deactivate(publicTenantId_2)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1

          val expectedEntity = tenantEntityRead_1.copy(id = res.head.id)
          res.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicTenantId" when {

      "the row is activated" should {

        "return deactivated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)

            res <- tenantDb.deactivate(publicTenantId_1)
          } yield res).transact(transactor)

          result.asserting(res => res shouldBe Right(deactivatedTenantEntityRead_1.copy(id = res.value.id)))
        }

        "deactivate this row" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)

            _ <- tenantDb.deactivate(publicTenantId_1)
            res <- Queries.getAllTenants
          } yield res).transact(transactor)

          result.asserting { res =>
            res.size shouldBe 1
            res.head shouldBe deactivatedTenantEntityRead_1.copy(id = res.head.id)
          }
        }
      }

      "the row is deactivated" should {

        "return deactivated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- tenantDb.deactivate(publicTenantId_1)

            res <- tenantDb.deactivate(publicTenantId_1)
          } yield res).transact(transactor)

          result.asserting(res => res shouldBe Right(deactivatedTenantEntityRead_1.copy(id = res.value.id)))
        }

        "make NO changes to the DB" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- tenantDb.deactivate(publicTenantId_1)

            _ <- tenantDb.deactivate(publicTenantId_1)
            res <- Queries.getAllTenants
          } yield res).transact(transactor)

          result.asserting { res =>
            res.size shouldBe 1
            res.head shouldBe deactivatedTenantEntityRead_1.copy(id = res.head.id)
          }
        }
      }
    }

    "there are several activated rows in the DB but only one with given publicTenantId" should {

      "return deactivated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)

          res <- tenantDb.deactivate(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(deactivatedTenantEntityRead_1.copy(id = res.value.id)))
      }

      "deactivate only this row and leave others unchanged" in {
        val result = (for {
          entityRead_1 <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_2 <- tenantDb.insert(tenantEntityWrite_2)
          entityRead_3 <- tenantDb.insert(tenantEntityWrite_3)

          _ <- tenantDb.deactivate(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_1, entityRead_2, entityRead_3) =>
          res.size shouldBe 3

          val expectedEntities = Seq(
            deactivatedTenantEntityRead_1.copy(id = entityRead_1.id),
            entityRead_2,
            entityRead_3
          )
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on deleteDeactivated" when {

    "there are no rows in the DB" should {

      "return Left containing TenantNotFoundError" in {
        tenantDb
          .deleteDeactivated(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.deleteDeactivated(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there is a deactivated row in the DB with different publicTenantId" should {

      "return Left containing TenantNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.deactivate(publicTenantId_1)

          res <- tenantDb.deleteDeactivated(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(TenantNotFoundError(publicTenantIdStr_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.deactivate(publicTenantId_1)

          _ <- tenantDb.deleteDeactivated(publicTenantId_2)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe List(deactivatedTenantEntityRead_1.copy(id = res.head.id)))
      }
    }

    "there is an activated row in the DB with given publicTenantId" should {

      "return Left containing TenantIsNotDeactivatedError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.deleteDeactivated(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(TenantIsNotDeactivatedError(publicTenantId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- tenantDb.deleteDeactivated(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe List(tenantEntityRead_1.copy(id = res.head.id)))
      }
    }

    "there is a deactivated row in the DB with given publicTenantId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.deactivate(publicTenantId_1)

          res <- tenantDb.deleteDeactivated(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(deactivatedTenantEntityRead_1.copy(id = res.value.id)))
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.deactivate(publicTenantId_1)

          _ <- tenantDb.deleteDeactivated(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[TenantEntity.Read])
      }
    }

    "there are several deactivated rows in the DB but only one with given publicTenantId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          _ <- tenantDb.deactivate(publicTenantId_1)
          _ <- tenantDb.deactivate(publicTenantId_2)
          _ <- tenantDb.deactivate(publicTenantId_3)

          res <- tenantDb.deleteDeactivated(publicTenantId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(deactivatedTenantEntityRead_1.copy(id = res.value.id)))
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- tenantDb.insert(tenantEntityWrite_3)
          _ <- tenantDb.deactivate(publicTenantId_1)
          entityRead_2 <- tenantDb.deactivate(publicTenantId_2)
          entityRead_3 <- tenantDb.deactivate(publicTenantId_3)

          _ <- tenantDb.deleteDeactivated(publicTenantId_1)
          res <- Queries.getAllTenants
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_2, entityRead_3) =>
          res.size shouldBe 2

          val expectedEntities = Seq(entityRead_2, entityRead_3)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "TenantDb on getByPublicTenantId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        tenantDb
          .getByPublicTenantId(publicTenantId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[TenantEntity.Read])
      }
    }

    "there is a row in the DB with different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- tenantDb.getByPublicTenantId(publicTenantId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[TenantEntity.Read])
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

}
