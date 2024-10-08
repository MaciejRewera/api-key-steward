package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.IntegrationTestData.{applicationEntityUpdate_1, _}
import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.ApplicationDbError.ApplicationInsertionError.{
  ApplicationAlreadyExistsError,
  ReferencedTenantDoesNotExistError
}
import apikeysteward.model.RepositoryErrors.ApplicationDbError.{
  ApplicationIsNotDeactivatedError,
  ApplicationNotFoundError
}
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ApplicationEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApplicationDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, application CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val applicationDb = new ApplicationDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApplications: doobie.ConnectionIO[List[ApplicationEntity.Read]] =
      sql"SELECT * FROM application".query[ApplicationEntity.Read].stream.compile.toList
  }

  "ApplicationDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          res <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(applicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val resultApplication = allApplications.head
          resultApplication shouldBe applicationEntityRead_1.copy(
            id = resultApplication.id,
            tenantId = resultApplication.tenantId
          )
        }
      }
    }

    "there is a row in the DB with a different applicationId" should {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(applicationEntityRead_2.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          res <- Queries.getAllApplications
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allApplications, entityRead_1, entityRead_2) =>
          allApplications.size shouldBe 2

          val expectedApplications = Seq(
            applicationEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            applicationEntityRead_2.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allApplications should contain theSameElementsAs expectedApplications
        }
      }
    }

    "there is a row in the DB with the same applicationId" should {

      "return Left containing ApplicationAlreadyExistsError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb
            .insert(applicationEntityWrite_2.copy(tenantId = tenantId, publicApplicationId = publicApplicationIdStr_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(ApplicationAlreadyExistsError(publicApplicationIdStr_1))
          res.left.value.message shouldBe s"Application with publicApplicationId = $publicApplicationIdStr_1 already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId)).transact(transactor)

          _ <- applicationDb
            .insert(applicationEntityWrite_2.copy(tenantId = tenantId, publicApplicationId = publicApplicationIdStr_1))
            .transact(transactor)
          res <- Queries.getAllApplications.transact(transactor)
        } yield res

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val resultApplication = allApplications.head
          resultApplication shouldBe applicationEntityRead_1.copy(
            id = resultApplication.id,
            tenantId = resultApplication.tenantId
          )
        }
      }
    }

    "there is no Tenant with provided tenantId in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        applicationDb
          .insert(applicationEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(applicationEntityWrite_1.tenantId)))
      }

      "NOT insert any Application into the DB" in {
        val result = for {
          _ <- applicationDb.insert(applicationEntityWrite_1).transact(transactor)
          res <- Queries.getAllApplications.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }
  }

  "ApplicationDb on update" when {

    val updatedEntityRead =
      applicationEntityRead_1.copy(name = applicationNameUpdated, description = applicationDescriptionUpdated)

    "there are NO rows in the DB" should {

      "return Left containing ApplicationNotFoundError" in {
        applicationDb
          .update(applicationEntityUpdate_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- applicationDb.update(applicationEntityUpdate_1)

          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a row in the DB with different publicApplicationId" should {

      "return Left containing ApplicationNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.update(applicationEntityUpdate_1.copy(publicApplicationId = publicApplicationIdStr_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          _ <- applicationDb.update(applicationEntityUpdate_1.copy(publicApplicationId = publicApplicationIdStr_2))
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val expectedEntity =
            applicationEntityRead_1.copy(id = allApplications.head.id, tenantId = allApplications.head.tenantId)
          allApplications.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicApplicationId" should {

      "return updated entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.update(applicationEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "update this row" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          _ <- applicationDb.update(applicationEntityUpdate_1)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val expectedEntity =
            updatedEntityRead.copy(id = allApplications.head.id, tenantId = allApplications.head.tenantId)
          allApplications.head shouldBe expectedEntity
        }
      }
    }

    "there are several rows in the DB but only one with given publicApplicationId" should {

      "return updated entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))

          res <- applicationDb.update(applicationEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "update only this row and leave others unchanged" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))

          _ <- applicationDb.update(applicationEntityUpdate_1)
          res <- Queries.getAllApplications
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allApplications, entityRead_1, entityRead_2, entityRead_3) =>
          allApplications.size shouldBe 3

          val expectedEntities = Seq(
            updatedEntityRead.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            entityRead_2,
            entityRead_3
          )
          allApplications should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApplicationDb on activate" when {

    val activatedEntityRead = applicationEntityRead_1.copy(deactivatedAt = None)

    "there are NO rows in the DB" should {

      "return Left containing ApplicationNotFoundError" in {
        applicationDb
          .activate(publicApplicationId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- applicationDb.activate(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a deactivated row in the DB with different publicApplicationId" should {

      "return Left containing ApplicationNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)

          res <- applicationDb.activate(publicApplicationId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)

          _ <- applicationDb.activate(publicApplicationId_2)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val expectedEntity =
            deactivatedApplicationEntityRead_1.copy(
              id = allApplications.head.id,
              tenantId = allApplications.head.tenantId
            )

          allApplications.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicApplicationId" when {

      "the row is deactivated" should {

        "return activated entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            _ <- applicationDb.deactivate(publicApplicationId_1)

            res <- applicationDb.activate(publicApplicationId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(activatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
          }
        }

        "activate this row" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            _ <- applicationDb.deactivate(publicApplicationId_1)

            _ <- applicationDb.activate(publicApplicationId_1)
            res <- Queries.getAllApplications
          } yield res).transact(transactor)

          result.asserting { allApplications =>
            allApplications.size shouldBe 1

            val entity = allApplications.head
            allApplications.head shouldBe activatedEntityRead.copy(id = entity.id, tenantId = entity.tenantId)
          }
        }
      }

      "the row is activated" should {

        "return activated entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

            res <- applicationDb.activate(publicApplicationId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(activatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
          }
        }

        "make NO changes to the DB" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

            _ <- applicationDb.activate(publicApplicationId_1)
            res <- Queries.getAllApplications
          } yield res).transact(transactor)

          result.asserting { allApplications =>
            allApplications.size shouldBe 1

            val entity = allApplications.head
            allApplications.head shouldBe activatedEntityRead.copy(id = entity.id, tenantId = entity.tenantId)
          }
        }
      }
    }

    "there are several deactivated rows in the DB but only one with given publicTenantId" should {

      "return deactivated entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)
          _ <- applicationDb.deactivate(publicApplicationId_2)
          _ <- applicationDb.deactivate(publicApplicationId_3)

          res <- applicationDb.activate(publicApplicationId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(activatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "deactivate only this row and leave others unchanged" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))
          entityRead_1 <- applicationDb.deactivate(publicApplicationId_1)
          entityRead_2 <- applicationDb.deactivate(publicApplicationId_2)
          entityRead_3 <- applicationDb.deactivate(publicApplicationId_3)

          _ <- applicationDb.activate(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allApplications, entityRead_1, entityRead_2, entityRead_3) =>
          allApplications.size shouldBe 3

          val expectedEntities = Seq(
            activatedEntityRead.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            entityRead_2,
            entityRead_3
          )

          allApplications should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApplicationDb on deactivate" when {

    "there are NO rows in the DB" should {

      "return Left containing ApplicationNotFoundError" in {
        applicationDb
          .deactivate(publicApplicationId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- applicationDb.deactivate(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is an activated row in the DB with different publicApplicationId" should {

      "return Left containing ApplicationNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.deactivate(publicApplicationId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          _ <- applicationDb.deactivate(publicApplicationId_2)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { allApplications =>
          allApplications.size shouldBe 1

          val expectedEntity =
            applicationEntityRead_1.copy(id = allApplications.head.id, tenantId = allApplications.head.tenantId)

          allApplications.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicApplicationId" when {

      "the row is activated" should {

        "return deactivated entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

            res <- applicationDb.deactivate(publicApplicationId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(
              deactivatedApplicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
            )
          }
        }

        "deactivate this row" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

            _ <- applicationDb.deactivate(publicApplicationId_1)
            res <- Queries.getAllApplications
          } yield res).transact(transactor)

          result.asserting { allApplications =>
            allApplications.size shouldBe 1

            val entity = allApplications.head
            allApplications.head shouldBe deactivatedApplicationEntityRead_1.copy(
              id = entity.id,
              tenantId = entity.tenantId
            )
          }
        }
      }

      "the row is deactivated" should {

        "return deactivated entity" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            _ <- applicationDb.deactivate(publicApplicationId_1)

            res <- applicationDb.deactivate(publicApplicationId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(
              deactivatedApplicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
            )
          }
        }

        "make NO changes to the DB" in {
          val result = (for {
            tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
            _ <- applicationDb.deactivate(publicApplicationId_1)

            _ <- applicationDb.deactivate(publicApplicationId_1)
            res <- Queries.getAllApplications
          } yield res).transact(transactor)

          result.asserting { allApplications =>
            allApplications.size shouldBe 1

            val entity = allApplications.head
            allApplications.head shouldBe deactivatedApplicationEntityRead_1.copy(
              id = entity.id,
              tenantId = entity.tenantId
            )
          }
        }
      }
    }

    "there are several activated rows in the DB but only one with given publicTenantId" should {

      "return deactivated entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))

          res <- applicationDb.deactivate(publicApplicationId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(
            deactivatedApplicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
          )
        }
      }

      "deactivate only this row and leave others unchanged" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))

          _ <- applicationDb.deactivate(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allApplications, entityRead_1, entityRead_2, entityRead_3) =>
          allApplications.size shouldBe 3

          val expectedEntities = Seq(
            deactivatedApplicationEntityRead_1.copy(
              id = entityRead_1.id,
              tenantId = entityRead_1.tenantId
            ),
            entityRead_2,
            entityRead_3
          )

          allApplications should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApplicationDb on deleteDeactivated" when {

    "there are no rows in the DB" should {

      "return Left containing ApplicationNotFoundError" in {
        applicationDb
          .deleteDeactivated(publicApplicationId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- applicationDb.deleteDeactivated(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a deactivated row in the DB with different publicApplicationId" should {

      "return Left containing ApplicationNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)

          res <- applicationDb.deleteDeactivated(publicApplicationId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApplicationNotFoundError(publicApplicationIdStr_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)

          _ <- applicationDb.deleteDeactivated(publicApplicationId_2)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(deactivatedApplicationEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is an activated row in the DB with given publicApplicationId" should {

      "return Left containing ApplicationIsNotDeactivatedError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.deleteDeactivated(publicApplicationId_1)
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Left(ApplicationIsNotDeactivatedError(publicApplicationId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          _ <- applicationDb.deleteDeactivated(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(applicationEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is a deactivated row in the DB with given publicApplicationId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)

          res <- applicationDb.deleteDeactivated(publicApplicationId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(deactivatedApplicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "delete this row from the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)

          _ <- applicationDb.deleteDeactivated(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there are several deactivated rows in the DB but only one with given publicApplicationId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)
          _ <- applicationDb.deactivate(publicApplicationId_2)
          _ <- applicationDb.deactivate(publicApplicationId_3)

          res <- applicationDb.deleteDeactivated(publicApplicationId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(deactivatedApplicationEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))
          _ <- applicationDb.deactivate(publicApplicationId_1)
          entityRead_2 <- applicationDb.deactivate(publicApplicationId_2)
          entityRead_3 <- applicationDb.deactivate(publicApplicationId_3)

          _ <- applicationDb.deleteDeactivated(publicApplicationId_1)
          res <- Queries.getAllApplications
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (res, entityRead_2, entityRead_3) =>
          res.size shouldBe 2

          val expectedEntities = Seq(entityRead_2, entityRead_3)
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

  }

  "ApplicationDb on getByPublicApplicationId" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        applicationDb
          .getByPublicApplicationId(publicApplicationId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[ApplicationEntity.Read])
      }
    }

    "there is a row in the DB with different publicApplicationId" should {
      "return empty Option" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.getByPublicApplicationId(publicApplicationId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApplicationEntity.Read])
      }
    }

    "there is a row in the DB with the same publicApplicationId" should {
      "return this entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.getByPublicApplicationId(publicApplicationId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(applicationEntityRead_1.copy(id = res.get.id, tenantId = res.get.tenantId))
        }
      }
    }
  }

  "ApplicationDb on getAll" when {

    "there are no rows in the DB" should {
      "return empty Stream" in {
        applicationDb.getAll.compile.toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a single row in the DB" should {
      "return this single row" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.getAll.compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res shouldBe List(applicationEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there are several rows in the DB" should {
      "return all rows" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))
          expectedEntities = Seq(entityRead_1, entityRead_2, entityRead_3).map(_.value)

          res <- applicationDb.getAll.compile.toList
        } yield (res, expectedEntities)).transact(transactor)

        result.asserting { case (res, expectedEntities) =>
          res.size shouldBe 3
          res should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApplicationDb on getAllForTenant" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        applicationDb
          .getAllForTenant(publicTenantId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a Tenant in the DB, but there are no Applications for this Tenant" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- applicationDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
      }
    }

    "there is a Tenant in the DB with a single Application" should {
      "return this single Application" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))

          res <- applicationDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(applicationEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is a Tenant in the DB with multiple Applications" should {
      "return all these Applications" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId))
          expectedEntities = Seq(entityRead_1, entityRead_2, entityRead_3).map(_.value)

          res <- applicationDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield (res, expectedEntities)).transact(transactor)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are several Tenants in the DB with associated Applications" when {

      "there are NO Applications for given publicTenantId" should {
        "return empty Stream" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            _ <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId_2))

            res <- applicationDb.getAllForTenant(publicTenantId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[ApplicationEntity.Read])
        }
      }

      "there is a single Application for given publicTenantId" should {
        "return this single Application" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            tenantId_3 <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId_3))

            res <- applicationDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, entityRead_1.value)).transact(transactor)

          result.asserting { case (res, entityRead_1) =>
            res shouldBe List(entityRead_1)
          }
        }
      }

      "there are several Applications for given publicTenantId" should {
        "return all these Applications" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- applicationDb.insert(applicationEntityWrite_1.copy(tenantId = tenantId_1))
            entityRead_2 <- applicationDb.insert(applicationEntityWrite_2.copy(tenantId = tenantId_1))
            _ <- applicationDb.insert(applicationEntityWrite_3.copy(tenantId = tenantId_2))

            expectedEntities = Seq(entityRead_1, entityRead_2).map(_.value)

            res <- applicationDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, expectedEntities)).transact(transactor)

          result.asserting { case (res, expectedEntities) =>
            res should contain theSameElementsAs expectedEntities
          }
        }
      }
    }
  }

}
