package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ResourceServersTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.model.RepositoryErrors.ResourceServerDbError.ResourceServerInsertionError._
import apikeysteward.model.RepositoryErrors.ResourceServerDbError._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ResourceServerEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ResourceServerDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, resource_server CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val resourceServerDb = new ResourceServerDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllResourceServers: doobie.ConnectionIO[List[ResourceServerEntity.Read]] =
      sql"SELECT * FROM resource_server".query[ResourceServerEntity.Read].stream.compile.toList
  }

  "ResourceServerDb on insert" when {

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        resourceServerDb
          .insert(resourceServerEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(resourceServerEntityWrite_1.tenantId)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)

          _ <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantDbId_2)).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- resourceServerDb.insert(resourceServerEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(resourceServerEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting { allResourceServers =>
          allResourceServers.size shouldBe 1

          val resultResourceServer = allResourceServers.head
          resultResourceServer shouldBe resourceServerEntityRead_1.copy(
            id = resultResourceServer.id,
            tenantId = resultResourceServer.tenantId
          )
        }
      }
    }

    "there is a row in the DB with a different publicResourceServerId" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(resourceServerEntityRead_2.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_1 <- resourceServerDb.insert(resourceServerEntityWrite_1)

          entityRead_2 <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          res <- Queries.getAllResourceServers
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allResourceServers, entityRead_1, entityRead_2) =>
          allResourceServers.size shouldBe 2

          val expectedResourceServers = Seq(
            resourceServerEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            resourceServerEntityRead_2.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allResourceServers should contain theSameElementsAs expectedResourceServers
        }
      }
    }

    "there is a row in the DB with the same publicResourceServerId" should {

      "return Left containing ResourceServerAlreadyExistsError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb
            .insert(
              resourceServerEntityWrite_2
                .copy(tenantId = tenantDbId_1, publicResourceServerId = publicResourceServerIdStr_1)
            )
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(ResourceServerAlreadyExistsError(publicResourceServerIdStr_1))
          res.left.value.message shouldBe s"ResourceServer with publicResourceServerId = [$publicResourceServerIdStr_1] already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)

          _ <- resourceServerDb
            .insert(
              resourceServerEntityWrite_2
                .copy(tenantId = tenantDbId_1, publicResourceServerId = publicResourceServerIdStr_1)
            )
            .transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting { allResourceServers =>
          allResourceServers.size shouldBe 1

          val resultResourceServer = allResourceServers.head
          resultResourceServer shouldBe resourceServerEntityRead_1.copy(
            id = resultResourceServer.id,
            tenantId = resultResourceServer.tenantId
          )
        }
      }
    }
  }

  "ResourceServerDb on update" when {

    val updatedEntityRead =
      resourceServerEntityRead_1.copy(name = resourceServerNameUpdated, description = resourceServerDescriptionUpdated)

    "there are no Tenants in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .update(publicTenantId_1, resourceServerEntityUpdate_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are NO rows in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1)
        } yield res).transact(transactor)

        result
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a row in the DB for a different publicTenantId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.update(publicTenantId_2, resourceServerEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)

          _ <- resourceServerDb.update(publicTenantId_2, resourceServerEntityUpdate_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(resourceServerEntityRead_1))
      }
    }

    "there is a row in the DB with different publicResourceServerId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.update(
            publicTenantId_1,
            resourceServerEntityUpdate_1.copy(publicResourceServerId = publicResourceServerIdStr_2)
          )
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- resourceServerDb.update(
            publicTenantId_1,
            resourceServerEntityUpdate_1.copy(publicResourceServerId = publicResourceServerIdStr_2)
          )
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(resourceServerEntityRead_1))
      }
    }

    "there is a row in the DB with given publicResourceServerId" should {

      "return updated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "update this row" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting { allResourceServers =>
          allResourceServers.size shouldBe 1

          val expectedEntity =
            updatedEntityRead.copy(id = allResourceServers.head.id, tenantId = allResourceServers.head.tenantId)
          allResourceServers.head shouldBe expectedEntity
        }
      }
    }

    "there are several rows in the DB but only one with given publicResourceServerId" should {

      "return updated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))

          res <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "update only this row and leave others unchanged" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_1 <- resourceServerDb.insert(resourceServerEntityWrite_1)
          entityRead_2 <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          entityRead_3 <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))

          _ <- resourceServerDb.update(publicTenantId_1, resourceServerEntityUpdate_1)
          res <- Queries.getAllResourceServers
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allResourceServers, entityRead_1, entityRead_2, entityRead_3) =>
          allResourceServers.size shouldBe 3

          val expectedEntities = Seq(
            updatedEntityRead.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            entityRead_2,
            entityRead_3
          )
          allResourceServers should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ResourceServerDb on activate" when {

    val activatedEntityRead = resourceServerEntityRead_1.copy(deactivatedAt = None)

    "there are no Tenants in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .activate(publicTenantId_1, publicResourceServerId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are NO rows in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .activate(publicTenantId_1, publicResourceServerId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a deactivated row in the DB for a different publicTenantId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          res <- resourceServerDb.activate(publicTenantId_2, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1).transact(transactor)

          _ <- resourceServerDb.activate(publicTenantId_2, publicResourceServerId_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(deactivatedResourceServerEntityRead_1))
      }
    }

    "there is a deactivated row in the DB with different publicResourceServerId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          res <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          _ <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_2)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(deactivatedResourceServerEntityRead_1))
      }
    }

    "there is a row in the DB with given publicResourceServerId" when {

      "the row is deactivated" should {

        "return activated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

            res <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(activatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
          }
        }

        "activate this row" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

            _ <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
            res <- Queries.getAllResourceServers
          } yield res).transact(transactor)

          result.asserting { allResourceServers =>
            allResourceServers.size shouldBe 1

            val entity = allResourceServers.head
            allResourceServers.head shouldBe activatedEntityRead.copy(id = entity.id, tenantId = entity.tenantId)
          }
        }
      }

      "the row is activated" should {

        "return activated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

            res <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(activatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
          }
        }

        "make NO changes to the DB" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

            _ <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
            res <- Queries.getAllResourceServers
          } yield res).transact(transactor)

          result.asserting { allResourceServers =>
            allResourceServers.size shouldBe 1

            val entity = allResourceServers.head
            allResourceServers.head shouldBe activatedEntityRead.copy(id = entity.id, tenantId = entity.tenantId)
          }
        }
      }
    }

    "there are several deactivated rows in the DB but only one with given publicTenantId" should {

      "return deactivated entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_2)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_3)

          res <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(activatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "deactivate only this row and leave others unchanged" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))
          entityRead_1 <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          entityRead_2 <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_2)
          entityRead_3 <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_3)

          _ <- resourceServerDb.activate(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allResourceServers, entityRead_1, entityRead_2, entityRead_3) =>
          allResourceServers.size shouldBe 3

          val expectedEntities = Seq(
            activatedEntityRead.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            entityRead_2,
            entityRead_3
          )

          allResourceServers should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ResourceServerDb on deactivate" when {

    "there are no Tenants in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .deactivate(publicTenantId_1, publicResourceServerId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are NO rows in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .deactivate(publicTenantId_1, publicResourceServerId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is an activated row in the DB for different publicTenantId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.deactivate(publicTenantId_2, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)

          _ <- resourceServerDb.deactivate(publicTenantId_2, publicResourceServerId_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(resourceServerEntityRead_1))
      }
    }

    "there is an activated row in the DB with different publicResourceServerId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_2)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting { allResourceServers =>
          allResourceServers.size shouldBe 1

          val expectedEntity =
            resourceServerEntityRead_1.copy(
              id = allResourceServers.head.id,
              tenantId = allResourceServers.head.tenantId
            )

          allResourceServers.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicResourceServerId" when {

      "the row is activated" should {

        "return deactivated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

            res <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(
              deactivatedResourceServerEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
            )
          }
        }

        "deactivate this row" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

            _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
            res <- Queries.getAllResourceServers
          } yield res).transact(transactor)

          result.asserting { allResourceServers =>
            allResourceServers.size shouldBe 1

            val entity = allResourceServers.head
            allResourceServers.head shouldBe deactivatedResourceServerEntityRead_1.copy(
              id = entity.id,
              tenantId = entity.tenantId
            )
          }
        }
      }

      "the row is deactivated" should {

        "return deactivated entity" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

            res <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          } yield res).transact(transactor)

          result.asserting { res =>
            res shouldBe Right(
              deactivatedResourceServerEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
            )
          }
        }

        "make NO changes to the DB" in {
          val result = (for {
            _ <- tenantDb.insert(tenantEntityWrite_1)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
            _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

            _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
            res <- Queries.getAllResourceServers
          } yield res).transact(transactor)

          result.asserting { allResourceServers =>
            allResourceServers.size shouldBe 1

            val entity = allResourceServers.head
            allResourceServers.head shouldBe deactivatedResourceServerEntityRead_1.copy(
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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))

          res <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(
            deactivatedResourceServerEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
          )
        }
      }

      "deactivate only this row and leave others unchanged" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          entityRead_1 <- resourceServerDb.insert(resourceServerEntityWrite_1)
          entityRead_2 <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          entityRead_3 <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))

          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allResourceServers, entityRead_1, entityRead_2, entityRead_3) =>
          allResourceServers.size shouldBe 3

          val expectedEntities = Seq(
            deactivatedResourceServerEntityRead_1.copy(
              id = entityRead_1.id,
              tenantId = entityRead_1.tenantId
            ),
            entityRead_2,
            entityRead_3
          )

          allResourceServers should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ResourceServerDb on deleteDeactivated" when {

    "there are no Tenants in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        resourceServerDb
          .deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a deactivated row in the DB for different publicTenantId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          res <- resourceServerDb.deleteDeactivated(publicTenantId_2, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1).transact(transactor)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1).transact(transactor)

          _ <- resourceServerDb.deleteDeactivated(publicTenantId_2, publicResourceServerId_1).transact(transactor)
          res <- Queries.getAllResourceServers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(resourceServerEntityRead_1.copy(deactivatedAt = Some(nowInstant))))
      }
    }

    "there is a deactivated row in the DB with different publicResourceServerId" should {

      "return Left containing ResourceServerNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          res <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerNotFoundError(publicResourceServerIdStr_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          _ <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_2)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(
            deactivatedResourceServerEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId)
          )
        }
      }
    }

    "there is an activated row in the DB with given publicResourceServerId" should {

      "return Left containing ResourceServerIsNotDeactivatedError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ResourceServerIsNotDeactivatedError(publicResourceServerId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          _ <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(resourceServerEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is a deactivated row in the DB with given publicResourceServerId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          res <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(
            deactivatedResourceServerEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
          )
        }
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)

          _ <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are several deactivated rows in the DB but only one with given publicResourceServerId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_2)
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_3)

          res <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(
            deactivatedResourceServerEntityRead_1.copy(id = res.value.id, tenantId = res.value.tenantId)
          )
        }
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantDbId_1))
          _ <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_1)
          entityRead_2 <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_2)
          entityRead_3 <- resourceServerDb.deactivate(publicTenantId_1, publicResourceServerId_3)

          _ <- resourceServerDb.deleteDeactivated(publicTenantId_1, publicResourceServerId_1)
          res <- Queries.getAllResourceServers
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allResourceServers, entityRead_2, entityRead_3) =>
          allResourceServers.size shouldBe 2

          val expectedEntities = Seq(entityRead_2, entityRead_3)
          allResourceServers should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ResourceServerDb on getByPublicResourceServerId" when {

    "there are no Tenants in the DB" should {
      "return empty Option" in {
        resourceServerDb
          .getByPublicResourceServerId(publicTenantId_1, publicResourceServerId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[ResourceServerEntity.Read])
      }
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- resourceServerDb.getByPublicResourceServerId(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ResourceServerEntity.Read])
      }
    }

    "there is a row in the DB for different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.getByPublicResourceServerId(publicTenantId_2, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ResourceServerEntity.Read])
      }
    }

    "there is a row in the DB with different publicResourceServerId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.getByPublicResourceServerId(publicTenantId_1, publicResourceServerId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ResourceServerEntity.Read])
      }
    }

    "there is a row in the DB with the same publicResourceServerId" should {
      "return this entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1)

          res <- resourceServerDb.getByPublicResourceServerId(publicTenantId_1, publicResourceServerId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(resourceServerEntityRead_1.copy(id = res.get.id, tenantId = res.get.tenantId))
        }
      }
    }
  }

  "ResourceServerDb on getAllForTenant" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        resourceServerDb
          .getAllForTenant(publicTenantId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a Tenant in the DB, but with a different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId))

          res <- resourceServerDb.getAllForTenant(publicTenantId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a Tenant in the DB, but there are no ResourceServers for this Tenant" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- resourceServerDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there is a Tenant in the DB with a single ResourceServer" should {
      "return this single ResourceServer" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId))

          res <- resourceServerDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(resourceServerEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is a Tenant in the DB with multiple ResourceServers" should {
      "return all these ResourceServers" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantId))
          expectedEntities = Seq(entityRead_1, entityRead_2, entityRead_3).map(_.value)

          res <- resourceServerDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield (res, expectedEntities)).transact(transactor)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are several Tenants in the DB with associated ResourceServers" when {

      "there are NO ResourceServers for given publicTenantId" should {
        "return empty Stream" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            _ <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantId_2))

            res <- resourceServerDb.getAllForTenant(publicTenantId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
        }
      }

      "there is a single ResourceServer for given publicTenantId" should {
        "return this single ResourceServer" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            tenantId_3 <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantId_3))

            res <- resourceServerDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, entityRead_1.value)).transact(transactor)

          result.asserting { case (res, entityRead_1) =>
            res shouldBe List(entityRead_1)
          }
        }
      }

      "there are several ResourceServers for given publicTenantId" should {
        "return all these ResourceServers" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- resourceServerDb.insert(resourceServerEntityWrite_1.copy(tenantId = tenantId_1))
            entityRead_2 <- resourceServerDb.insert(resourceServerEntityWrite_2.copy(tenantId = tenantId_1))
            _ <- resourceServerDb.insert(resourceServerEntityWrite_3.copy(tenantId = tenantId_2))

            expectedEntities = Seq(entityRead_1, entityRead_2).map(_.value)

            res <- resourceServerDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, expectedEntities)).transact(transactor)

          result.asserting { case (res, expectedEntities) =>
            res should contain theSameElementsAs expectedEntities
          }
        }
      }
    }
  }

}
