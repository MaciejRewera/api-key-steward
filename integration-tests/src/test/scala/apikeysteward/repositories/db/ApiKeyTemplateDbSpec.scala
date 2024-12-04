package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ResourceServersTestData.resourceServerEntityWrite_1
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, publicUserId_3, userEntityWrite_1}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateNotFoundError
import apikeysteward.repositories.TestDataInsertions.{TemplateDbId, TenantDbId, UserDbId}
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, ApiKeyTemplatesUsersEntity, ResourceServerEntity}
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApiKeyTemplateDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, api_key_template CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val userDb = new UserDb
  private val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb

  private val apiKeyTemplateDb = new ApiKeyTemplateDb

  private object Queries extends DoobieCustomMeta {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApiKeyTemplates: doobie.ConnectionIO[List[ApiKeyTemplateEntity.Read]] =
      sql"SELECT * FROM api_key_template".query[ApiKeyTemplateEntity.Read].stream.compile.toList
  }

  "ApiKeyTemplateDb on insert" when {

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        apiKeyTemplateDb
          .insert(apiKeyTemplateEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(resourceServerEntityWrite_1.tenantId)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyTemplateEntityRead_1))
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
          res <- Queries.getAllApiKeyTemplates
        } yield (res, tenantId)).transact(transactor)

        result.asserting { case (allApiKeyTemplates, expectedTenantId) =>
          allApiKeyTemplates.size shouldBe 1

          val resultApiKeyTemplate = allApiKeyTemplates.head
          resultApiKeyTemplate shouldBe apiKeyTemplateEntityRead_1.copy(
            id = resultApiKeyTemplate.id,
            tenantId = expectedTenantId
          )
        }
      }
    }

    "there is a row in the DB with a different publicTemplateId" when {

      "return inserted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
        } yield (res, tenantId)).transact(transactor)

        result.asserting { case (res, tenantId) =>
          res shouldBe Right(apiKeyTemplateEntityRead_2.copy(id = res.value.id, tenantId = tenantId))
        }
      }

      "insert entity into DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          entityRead_2 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
          res <- Queries.getAllApiKeyTemplates
        } yield (res, entityRead_1.value, entityRead_2.value)).transact(transactor)

        result.asserting { case (allTemplates, entityRead_1, entityRead_2) =>
          allTemplates.size shouldBe 2

          val expectedTemplates = Seq(
            apiKeyTemplateEntityRead_1.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            apiKeyTemplateEntityRead_2.copy(id = entityRead_2.id, tenantId = entityRead_2.tenantId)
          )
          allTemplates should contain theSameElementsAs expectedTemplates
        }
      }
    }

    "there is a row in the DB with the same publicTemplateId" when {

      "return Left containing ApiKeyTemplateAlreadyExistsError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.insert(
            apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId, publicTemplateId = publicTemplateIdStr_1)
          )
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(ApiKeyTemplateAlreadyExistsError(publicTemplateIdStr_1))
          res.left.value.message shouldBe s"ApiKeyTemplate with publicTemplateId = [$publicTemplateIdStr_1] already exists."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id).transact(transactor)
          entityRead_1 <- apiKeyTemplateDb
            .insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
            .transact(transactor)

          _ <- apiKeyTemplateDb
            .insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId, publicTemplateId = publicTemplateIdStr_1))
            .transact(transactor)
          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield (res, entityRead_1.value)

        result.asserting { case (allTemplates, entityRead_1) =>
          allTemplates.size shouldBe 1

          allTemplates.head shouldBe apiKeyTemplateEntityRead_1.copy(
            id = entityRead_1.id,
            tenantId = entityRead_1.tenantId
          )
        }
      }
    }

    "there is no Tenant with provided tenantId in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        apiKeyTemplateDb
          .insert(apiKeyTemplateEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(apiKeyTemplateEntityWrite_1.tenantId)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).transact(transactor)
          res <- Queries.getAllApiKeyTemplates.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }
  }

  "ApiKeyTemplateDb on update" when {

    val updatedEntityRead =
      apiKeyTemplateEntityRead_1.copy(
        isDefault = true,
        name = apiKeyTemplateNameUpdated,
        description = apiKeyTemplateDescriptionUpdated,
        apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
      )

    "there is no Tenant in the DB" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        apiKeyTemplateDb
          .update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)

          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there are NO rows in the DB" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)

          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a row in the DB with different publicTenantId" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.update(publicTenantId_2, apiKeyTemplateEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          _ <- apiKeyTemplateDb.update(publicTenantId_2, apiKeyTemplateEntityUpdate_1)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting { allApiKeyTemplates =>
          allApiKeyTemplates.size shouldBe 1

          val expectedEntity =
            apiKeyTemplateEntityRead_1.copy(
              id = allApiKeyTemplates.head.id,
              tenantId = allApiKeyTemplates.head.tenantId
            )
          allApiKeyTemplates.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with different publicApiKeyTemplateId" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.update(
            publicTenantId_1,
            apiKeyTemplateEntityUpdate_1.copy(publicTemplateId = publicTemplateIdStr_2)
          )
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_2)))
      }

      "make NO changes to the DB" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          _ <- apiKeyTemplateDb.update(
            publicTenantId_1,
            apiKeyTemplateEntityUpdate_1.copy(publicTemplateId = publicTemplateIdStr_2)
          )
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting { allApiKeyTemplates =>
          allApiKeyTemplates.size shouldBe 1

          val expectedEntity =
            apiKeyTemplateEntityRead_1.copy(
              id = allApiKeyTemplates.head.id,
              tenantId = allApiKeyTemplates.head.tenantId
            )
          allApiKeyTemplates.head shouldBe expectedEntity
        }
      }
    }

    "there is a row in the DB with given publicApiKeyTemplateId" should {

      "return updated entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "update this row" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          _ <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting { allApiKeyTemplates =>
          allApiKeyTemplates.size shouldBe 1

          val expectedEntity =
            updatedEntityRead.copy(id = allApiKeyTemplates.head.id, tenantId = allApiKeyTemplates.head.tenantId)
          allApiKeyTemplates.head shouldBe expectedEntity
        }
      }
    }

    "there are several rows in the DB but only one with given publicApiKeyTemplateId" should {

      "return updated entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Right(updatedEntityRead.copy(id = res.value.id, tenantId = res.value.tenantId))
        }
      }

      "update only this row and leave others unchanged" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

          _ <- apiKeyTemplateDb.update(publicTenantId_1, apiKeyTemplateEntityUpdate_1)
          res <- Queries.getAllApiKeyTemplates
        } yield (res, entityRead_1.value, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allApiKeyTemplates, entityRead_1, entityRead_2, entityRead_3) =>
          allApiKeyTemplates.size shouldBe 3

          val expectedEntities = Seq(
            updatedEntityRead.copy(id = entityRead_1.id, tenantId = entityRead_1.tenantId),
            entityRead_2,
            entityRead_3
          )
          allApiKeyTemplates should contain theSameElementsAs expectedEntities
        }
      }
    }
  }

  "ApiKeyTemplateDb on delete" when {

    "there is no Tenant in the DB" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        apiKeyTemplateDb
          .delete(publicTenantId_1, publicTemplateId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there are no rows in the DB" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a row in the DB under different publicTenantId" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.delete(publicTenantId_2, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          _ <- apiKeyTemplateDb.delete(publicTenantId_2, publicTemplateId_1)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyTemplateEntityRead_1))
      }
    }

    "there is a row in the DB with a different publicTemplateId" should {

      "return Left containing ApiKeyTemplateNotFoundError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          _ <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_2)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(apiKeyTemplateEntityRead_1))
      }
    }

    "there is a row in the DB with the same both publicTenantId and publicTemplateId" should {

      "return deleted entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyTemplateEntityRead_1))
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          _ <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllApiKeyTemplates
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there are several rows in the DB but only one with the same both publicTenantId and publicTemplateId" should {

      "return deleted entity" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(apiKeyTemplateEntityRead_1))
      }

      "delete this row from the DB and leave others intact" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))

          _ <- apiKeyTemplateDb.delete(publicTenantId_1, publicTemplateId_1)
          res <- Queries.getAllApiKeyTemplates
        } yield (res, entityRead_2.value, entityRead_3.value)).transact(transactor)

        result.asserting { case (allApiKeyTemplates, entityRead_2, entityRead_3) =>
          allApiKeyTemplates.size shouldBe 2

          allApiKeyTemplates should contain theSameElementsAs Seq(entityRead_2, entityRead_3)
        }
      }
    }
  }

  "ApiKeyTemplateDb on getByPublicTemplateId" when {

    "there is no Tenant in the DB" should {
      "return empty Option" in {
        apiKeyTemplateDb
          .getByPublicTemplateId(publicTenantId_1, publicTemplateId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[ApiKeyTemplateEntity.Read])
      }
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyTemplateDb.getByPublicTemplateId(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a row in the DB with different publicTenantId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.getByPublicTemplateId(publicTenantId_2, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a row in the DB with different publicTemplateId" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.getByPublicTemplateId(publicTenantId_1, publicTemplateId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a row in the DB with the same publicTemplateId" should {
      "return this entity" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.getByPublicTemplateId(publicTenantId_1, publicTemplateId_1)
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Some(apiKeyTemplateEntityRead_1.copy(id = res.get.id, tenantId = res.get.tenantId))
        }
      }
    }
  }

  "ApiKeyTemplateDb on getAllForUser" when {

    "there is no Tenant in the DB" should {
      "return empty Stream" in {
        apiKeyTemplateDb
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there are NO Users in the DB" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.getAllForUser(publicTenantId_2, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a User in the DB, but under a different Tenant" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          userId <- userDb.insert(userEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = userId)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplateDb.getAllForUser(publicTenantId_2, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a User in the DB, but with a different publicUserId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          userId <- userDb.insert(userEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = userId)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a User in the DB, but there are no ApiKeyTemplatesUsers for this User" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          _ <- userDb.insert(userEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a User in the DB with a single ApiKeyTemplatesUsers" should {
      "return this single ApiKeyTemplate" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          userId <- userDb.insert(userEntityWrite_1).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = userId)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          expectedApiKeyTemplateEntities = List(apiKeyTemplateEntityRead_1)

          res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_1).compile.toList
        } yield (res, expectedApiKeyTemplateEntities)).transact(transactor)

        result.asserting { case (res, expectedApiKeyTemplateEntities) =>
          res shouldBe expectedApiKeyTemplateEntities
        }
      }
    }

    "there is a User in the DB with multiple ApiKeyTemplatesUsers" should {
      "return all these ApiKeyTemplates" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          userId <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          templateId_1 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          templateId_2 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
          templateId_3 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId)).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId_1, userId = userId),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId_2, userId = userId),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId_3, userId = userId)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          expectedApiKeyTemplateEntities = List(
            apiKeyTemplateEntityRead_1.copy(id = templateId_1, tenantId = tenantId),
            apiKeyTemplateEntityRead_2.copy(id = templateId_2, tenantId = tenantId),
            apiKeyTemplateEntityRead_3.copy(id = templateId_3, tenantId = tenantId)
          )

          res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_1).compile.toList
        } yield (res, expectedApiKeyTemplateEntities)).transact(transactor)

        result.asserting { case (res, expectedApiKeyTemplateEntities) =>
          res.size shouldBe 3
          res should contain theSameElementsAs expectedApiKeyTemplateEntities
        }
      }
    }

    "there are several Users in the DB with associated ApiKeyTemplatesUsers" when {

      def insertPrerequisiteData(): ConnectionIO[(TenantDbId, List[TemplateDbId], List[UserDbId])] =
        TestDataInsertions.insertPrerequisiteTemplatesAndUsers(tenantDb, userDb, apiKeyTemplateDb)

      "there are NO ApiKeyTemplatesUsers for given User" should {
        "return empty Stream" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (_, templateIds, userIds) = dataIds

            preExistingEntities = List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(1)),
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
            )
            _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

            res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
        }
      }

      "there is a single ApiKeyTemplatesUsers for given User" should {
        "return this single ApiKeyTemplate" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (tenantId, templateIds, userIds) = dataIds

            preExistingEntityExpectedToBeFetched = List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
            )

            preExistingEntities = preExistingEntityExpectedToBeFetched ++ List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1)),
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(1))
            )
            _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

            expectedApiKeyTemplateEntities = List(
              apiKeyTemplateEntityRead_1.copy(id = templateIds.head, tenantId = tenantId)
            )

            res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_1).compile.toList
          } yield (res, expectedApiKeyTemplateEntities)).transact(transactor)

          result.asserting { case (res, expectedApiKeyTemplateEntities) =>
            res shouldBe expectedApiKeyTemplateEntities
          }
        }
      }

      "there are several ApiKeyTemplatesUsers for given User" should {
        "return all these ApiKeyTemplates" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (tenantId, templateIds, userIds) = dataIds

            preExistingEntitiesExpectedToBeFetched = List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head),
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds(1))
            )

            preExistingEntities = preExistingEntitiesExpectedToBeFetched ++ List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(1)),
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
            )
            _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

            expectedApiKeyTemplateEntities = List(
              apiKeyTemplateEntityRead_1.copy(id = templateIds.head, tenantId = tenantId),
              apiKeyTemplateEntityRead_2.copy(id = templateIds(1), tenantId = tenantId)
            )

            res <- apiKeyTemplateDb.getAllForUser(publicTenantId_1, publicUserId_1).compile.toList
          } yield (res, expectedApiKeyTemplateEntities)).transact(transactor)

          result.asserting { case (res, expectedApiKeyTemplateEntities) =>
            res.size shouldBe 2
            res should contain theSameElementsAs expectedApiKeyTemplateEntities
          }
        }
      }
    }
  }

  "ApiKeyTemplateDb on getAllForTenant" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        apiKeyTemplateDb
          .getAllForTenant(publicTenantId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a Tenant in the DB, but with a different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a Tenant in the DB, but there are no ApiKeyTemplates for this Tenant" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
      }
    }

    "there is a Tenant in the DB with a single ApiKeyTemplate" should {
      "return this single ApiKeyTemplate" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))

          res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe List(apiKeyTemplateEntityRead_1.copy(id = res.head.id, tenantId = res.head.tenantId))
        }
      }
    }

    "there is a Tenant in the DB with multiple ApiKeyTemplates" should {
      "return all these ApiKeyTemplates" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          entityRead_1 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId))
          entityRead_2 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId))
          entityRead_3 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId))
          expectedEntities = Seq(entityRead_1, entityRead_2, entityRead_3).map(_.value)

          res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_1).compile.toList
        } yield (res, expectedEntities)).transact(transactor)

        result.asserting { case (res, expectedEntities) =>
          res should contain theSameElementsAs expectedEntities
        }
      }
    }

    "there are several Tenants in the DB with associated ApiKeyTemplates" when {

      "there are NO ApiKeyTemplates for given publicTenantId" should {
        "return empty Stream" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId_2))

            res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[ApiKeyTemplateEntity.Read])
        }
      }

      "there is a single ApiKeyTemplate for given publicTenantId" should {
        "return this single ApiKeyTemplate" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            tenantId_3 <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId_1))
            _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId_2))
            _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId_3))

            res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, entityRead_1.value)).transact(transactor)

          result.asserting { case (res, entityRead_1) =>
            res shouldBe List(entityRead_1)
          }
        }
      }

      "there are several ApiKeyTemplates for given publicTenantId" should {
        "return all these ApiKeyTemplates" in {
          val result = (for {
            tenantId_1 <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
            tenantId_2 <- tenantDb.insert(tenantEntityWrite_2).map(_.value.id)
            _ <- tenantDb.insert(tenantEntityWrite_3).map(_.value.id)
            entityRead_1 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId_1))
            entityRead_2 <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_2.copy(tenantId = tenantId_1))
            _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_3.copy(tenantId = tenantId_2))

            expectedEntities = Seq(entityRead_1, entityRead_2).map(_.value)

            res <- apiKeyTemplateDb.getAllForTenant(publicTenantId_1).compile.toList
          } yield (res, expectedEntities)).transact(transactor)

          result.asserting { case (res, expectedEntities) =>
            res should contain theSameElementsAs expectedEntities
          }
        }
      }
    }
  }

}
