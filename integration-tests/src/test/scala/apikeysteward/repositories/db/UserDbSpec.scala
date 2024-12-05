package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.TenantsTestData._
import apikeysteward.base.testdata.UsersTestData._
import apikeysteward.model.RepositoryErrors.UserDbError.UserInsertionError._
import apikeysteward.model.RepositoryErrors.UserDbError.UserNotFoundError
import apikeysteward.repositories.TestDataInsertions.{TemplateDbId, TenantDbId, UserDbId}
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesUsersEntity, ResourceServerEntity, UserEntity}
import apikeysteward.repositories.{DatabaseIntegrationSpec, TestDataInsertions}
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
    _ <- sql"TRUNCATE tenant, tenant_user, api_key_template, api_key_templates_users CASCADE".update.run
  } yield ()

  private val tenantDb = new TenantDb
  private val apiKeyTemplateDb = new ApiKeyTemplateDb
  private val apiKeyTemplatesUsersDb = new ApiKeyTemplatesUsersDb

  private val userDb = new UserDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllUsers: doobie.ConnectionIO[List[UserEntity.Read]] =
      sql"SELECT * FROM tenant_user".query[UserEntity.Read].stream.compile.toList
  }

  "UserDb on insert" when {

    "there is no Tenant in the DB" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        userDb
          .insert(userEntityWrite_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- userDb.insert(userEntityWrite_1).transact(transactor)
          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }
    
    "there is a Tenant in the DB with a different tenantId" should {

      "return Left containing ReferencedTenantDoesNotExistError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantDbId_2))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError.fromDbId(tenantDbId_2)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)

          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantDbId_2)).transact(transactor)
          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- userDb.insert(userEntityWrite_1)

          res <- userDb.insert(userEntityWrite_2.copy(publicUserId = publicUserIdStr_1))
        } yield res).transact(transactor)

        result.asserting(res => res shouldBe Right(userEntityRead_2.copy(publicUserId = publicUserIdStr_1)))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- tenantDb.insert(tenantEntityWrite_2)
          _ <- userDb.insert(userEntityWrite_1)

          _ <- userDb.insert(userEntityWrite_2.copy(publicUserId = publicUserIdStr_1))
          res <- Queries.getAllUsers
        } yield res).transact(transactor)

        result.asserting { allUsers =>
          allUsers.size shouldBe 2

          val expectedUsers = Seq(
            userEntityRead_1,
            userEntityRead_2.copy(publicUserId = publicUserIdStr_1)
          )
          allUsers should contain theSameElementsAs expectedUsers
        }
      }
    }

    "there is a row in the DB with the same both tenantId and publicUserId" should {

      "return Left containing UserAlreadyExistsForThisTenantError" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

          res <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantDbId_1, publicUserId = publicUserId_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe Left(UserAlreadyExistsForThisTenantError(publicUserId_1, tenantDbId_1))
          res.left.value.message shouldBe s"User with publicUserId = $publicUserId_1 already exists for Tenant with ID = [$tenantDbId_1]."
        }
      }

      "NOT insert the second entity into DB" in {
        val result = for {
          _ <- tenantDb.insert(tenantEntityWrite_1).transact(transactor)
          _ <- userDb.insert(userEntityWrite_1).transact(transactor)

          _ <- userDb
            .insert(userEntityWrite_2.copy(tenantId = tenantDbId_1, publicUserId = publicUserId_1))
            .transact(transactor)
          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List(userEntityRead_1))
      }
    }
  }

  "UserDb on delete" when {

    "there is no Tenant in the DB" should {

      "return Left containing UserNotFoundError" in {
        userDb
          .delete(publicTenantId_1, publicUserId_1)
          .transact(transactor)
          .asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_1, publicUserId_1)))
      }

      "NOT insert any entity into the DB" in {
        val result = for {
          _ <- userDb.delete(publicTenantId_1, publicUserId_1).transact(transactor)
          res <- Queries.getAllUsers.transact(transactor)
        } yield res

        result.asserting(_ shouldBe List.empty[ResourceServerEntity.Read])
      }
    }

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

          res <- userDb.delete(publicTenantId_2, publicUserId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_2, publicUserId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

          res <- userDb.delete(publicTenantId_1, publicUserId_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_1, publicUserId_2)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

          res <- userDb.delete(publicTenantId_2, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Left(UserNotFoundError(publicTenantId_2, publicUserId_1)))
      }

      "make no changes to the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

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
          _ <- tenantDb.insert(tenantEntityWrite_1)
          entityRead <- userDb.insert(userEntityWrite_1)

          res <- userDb.delete(publicTenantId_1, publicUserId_1)
        } yield (res, entityRead.value)).transact(transactor)

        result.asserting { case (res, entityRead) =>
          res shouldBe Right(userEntityRead_1.copy(id = entityRead.id, tenantId = entityRead.tenantId))
        }
      }

      "delete this row from the DB" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

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

    "there are no Tenants in the DB" should {
      "return empty Option" in {
        userDb
          .getByPublicUserId(publicTenantId_1, publicUserId_1)
          .transact(transactor)
          .asserting(_ shouldBe none[UserEntity.Read])
      }
    }

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          res <- userDb.getByPublicUserId(publicTenantId_2, publicUserId_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[UserEntity.Read])
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

  "UserDb on getAllForTemplate" when {

    "there are no Tenants in the DB" should {
      "return empty Stream" in {
        userDb
          .getAllForTemplate(publicTenantId_1, publicTemplateId_1)
          .compile
          .toList
          .transact(transactor)
          .asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there are NO ApiKeyTemplates in the DB" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)
          _ <- userDb.insert(userEntityWrite_1)

          res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB, but with a different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- userDb.insert(userEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateDbId_1, userId = userDbId_1)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- userDb.getAllForTemplate(publicTenantId_2, publicTemplateId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB, but with a different publicTemplateId" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- userDb.insert(userEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateDbId_1, userId = userDbId_1)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB, but there are no ApiKeyTemplatesUsers for this Template" should {
      "return empty Stream" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- userDb.insert(userEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
      }
    }

    "there is an ApiKeyTemplate in the DB with a single ApiKeyTemplatesUsers" should {
      "return this single ApiKeyTemplatesUsers" in {
        val result = (for {
          _ <- tenantDb.insert(tenantEntityWrite_1)

          _ <- userDb.insert(userEntityWrite_1)
          _ <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateDbId_1, userId = userDbId_1)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          expectedUserEntities = List(userEntityRead_1.copy(id = userDbId_1, tenantId = tenantDbId_1))

          res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
        } yield (res, expectedUserEntities)).transact(transactor)

        result.asserting { case (res, expectedUserEntities) =>
          res shouldBe expectedUserEntities
        }
      }
    }

    "there is an ApiKeyTemplate in the DB with multiple ApiKeyTemplatesUsers" should {
      "return all these ApiKeyTemplatesUsers" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)

          userId_1 <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)
          userId_2 <- userDb.insert(userEntityWrite_2.copy(tenantId = tenantId)).map(_.value.id)
          userId_3 <- userDb.insert(userEntityWrite_3.copy(tenantId = tenantId)).map(_.value.id)
          templateId <- apiKeyTemplateDb.insert(apiKeyTemplateEntityWrite_1.copy(tenantId = tenantId)).map(_.value.id)

          preExistingEntities = List(
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = userId_1),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = userId_2),
            ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateId, userId = userId_3)
          )
          _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

          expectedUserEntities = List(
            userEntityRead_1.copy(id = userId_1, tenantId = tenantId),
            userEntityRead_2.copy(id = userId_2, tenantId = tenantId),
            userEntityRead_3.copy(id = userId_3, tenantId = tenantId)
          )

          res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
        } yield (res, expectedUserEntities)).transact(transactor)

        result.asserting { case (res, expectedUserEntities) =>
          res.size shouldBe 3
          res should contain theSameElementsAs expectedUserEntities
        }
      }
    }

    "there are several ApiKeyTemplates in the DB with associated ApiKeyTemplatesUsers" when {

      def insertPrerequisiteData(): ConnectionIO[(TenantDbId, List[TemplateDbId], List[UserDbId])] =
        TestDataInsertions.insertPrerequisiteTemplatesAndUsers(tenantDb, userDb, apiKeyTemplateDb)

      "there are NO ApiKeyTemplatesUsers for given publicTemplateId" should {
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

            res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_3).compile.toList
          } yield res).transact(transactor)

          result.asserting(_ shouldBe List.empty[UserEntity.Read])
        }
      }

      "there is a single ApiKeyTemplatesUsers for given publicTemplateId" should {
        "return single ApiKeyTemplate" in {
          val result = (for {
            dataIds <- insertPrerequisiteData()
            (tenantId, templateIds, userIds) = dataIds

            preExistingEntityExpectedToBeFetched = List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds.head, userId = userIds.head)
            )

            preExistingEntities = preExistingEntityExpectedToBeFetched ++ List(
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds(1)),
              ApiKeyTemplatesUsersEntity.Write(apiKeyTemplateId = templateIds(1), userId = userIds.head)
            )
            _ <- apiKeyTemplatesUsersDb.insertMany(preExistingEntities)

            expectedUserEntities = List(userEntityRead_1.copy(id = userIds.head, tenantId = tenantId))

            res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
          } yield (res, expectedUserEntities)).transact(transactor)

          result.asserting { case (res, expectedUserEntities) =>
            res.size shouldBe 1
            res shouldBe expectedUserEntities
          }
        }
      }

      "there are several ApiKeyTemplatesUsers got given publicTemplateId" should {
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

            expectedUserEntities = List(
              userEntityRead_1.copy(id = userIds.head, tenantId = tenantId),
              userEntityRead_2.copy(id = userIds(1), tenantId = tenantId)
            )

            res <- userDb.getAllForTemplate(publicTenantId_1, publicTemplateId_1).compile.toList
          } yield (res, expectedUserEntities)).transact(transactor)

          result.asserting { case (res, expectedUserEntities) =>
            res.size shouldBe 2
            res should contain theSameElementsAs expectedUserEntities
          }
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

    "there is a Tenant in the DB, but with a different publicTenantId" should {
      "return empty Stream" in {
        val result = (for {
          tenantId <- tenantDb.insert(tenantEntityWrite_1).map(_.value.id)
          _ <- userDb.insert(userEntityWrite_1.copy(tenantId = tenantId))

          res <- userDb.getAllForTenant(publicTenantId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[UserEntity.Read])
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
