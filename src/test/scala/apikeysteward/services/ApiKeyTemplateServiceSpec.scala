package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.publicTenantId_1
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, publicUserId_2, publicUserId_3, user_1}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError.ApiKeyTemplateInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplateDbError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.{
  ApiKeyTemplatesPermissionsInsertionError,
  ApiKeyTemplatesPermissionsNotFoundError
}
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKeyTemplate, ApiKeyTemplateUpdate}
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
import apikeysteward.repositories._
import apikeysteward.routes.model.admin.apikeytemplate._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, times, verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException
import java.util.UUID

class ApiKeyTemplateServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val uuidGenerator = mock[UuidGenerator]
  private val apiKeyTemplateRepository = mock[ApiKeyTemplateRepository]
  private val userRepository = mock[UserRepository]
  private val apiKeyTemplatesPermissionsRepository = mock[ApiKeyTemplatesPermissionsRepository]
  private val apiKeyTemplatesUsersRepository = mock[ApiKeyTemplatesUsersRepository]

  private val apiKeyTemplateService = new ApiKeyTemplateService(
    uuidGenerator,
    apiKeyTemplateRepository,
    userRepository,
    apiKeyTemplatesPermissionsRepository,
    apiKeyTemplatesUsersRepository
  )

  override def beforeEach(): Unit =
    reset(
      uuidGenerator,
      apiKeyTemplateRepository,
      userRepository,
      apiKeyTemplatesPermissionsRepository,
      apiKeyTemplatesUsersRepository
    )

  private val testException = new RuntimeException("Test Exception")
  private val testSqlException = new SQLException("Test SQL Exception")

  private val apiKeyTemplatesPermissionsInsertionErrors = Seq(
    ApiKeyTemplatesPermissionsAlreadyExistsError(101L, 102L),
    ApiKeyTemplatesPermissionsInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1),
    ReferencedPermissionDoesNotExistError(publicPermissionId_1),
    ApiKeyTemplatesPermissionsInsertionErrorImpl(testSqlException)
  )

  private val inputPublicPermissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
  private val inputPublicUserIds = List(publicUserId_1, publicUserId_2, publicUserId_3)

  "ApiKeyTemplateService on createApiKeyTemplate" when {

    val createApiKeyTemplateRequest = CreateApiKeyTemplateRequest(
      name = apiKeyTemplateName_1,
      description = apiKeyTemplateDescription_1,
      isDefault = false,
      apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriod_1,
      apiKeyPrefix = apiKeyPrefix_1
    )

    val apiKeyTemplate = apiKeyTemplate_1.copy(permissions = List.empty)

    val apiKeyTemplateAlreadyExistsError = ApiKeyTemplateAlreadyExistsError(publicTemplateIdStr_1)

    "everything works correctly" should {

      "call UuidGenerator and ApiKeyTemplateRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicTemplateId_1)
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.pure(Right(apiKeyTemplate))

        for {
          _ <- apiKeyTemplateService.createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)

          _ = verify(uuidGenerator).generateUuid
          _ = verify(apiKeyTemplateRepository).insert(eqTo(publicTenantId_1), eqTo(apiKeyTemplate))
        } yield ()
      }

      "return the newly created ApiKeyTemplate returned by ApiKeyTemplateRepository" in {
        uuidGenerator.generateUuid returns IO.pure(publicTemplateId_1)
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.pure(Right(apiKeyTemplate))

        apiKeyTemplateService
          .createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)
          .asserting(_ shouldBe Right(apiKeyTemplate))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call ApiKeyTemplateRepository" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- apiKeyTemplateService.createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest).attempt

          _ = verifyZeroInteractions(apiKeyTemplateRepository)
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        apiKeyTemplateService
          .createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateRepository returns Left containing ApiKeyTemplateAlreadyExistsError on the first try" should {

      "call UuidGenerator and ApiKeyTemplateRepository again" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicTemplateId_1),
          IO.pure(publicTemplateId_2)
        )
        val insertedApiKeyTemplate = apiKeyTemplate.copy(publicTemplateId = publicTemplateId_2)
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns (
          IO.pure(Left(apiKeyTemplateAlreadyExistsError)),
          IO.pure(Right(insertedApiKeyTemplate))
        )

        for {
          _ <- apiKeyTemplateService.createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)

          _ = verify(uuidGenerator, times(2)).generateUuid
          _ = verify(apiKeyTemplateRepository).insert(eqTo(publicTenantId_1), eqTo(apiKeyTemplate))
          _ = verify(apiKeyTemplateRepository).insert(eqTo(publicTenantId_1), eqTo(insertedApiKeyTemplate))
        } yield ()
      }

      "return the second created ApiKeyTemplate returned by ApiKeyTemplateRepository" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicTemplateId_1),
          IO.pure(publicTemplateId_2)
        )
        val insertedApiKeyTemplate = apiKeyTemplate.copy(publicTemplateId = publicTemplateId_2)
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns (
          IO.pure(Left(apiKeyTemplateAlreadyExistsError)),
          IO.pure(Right(insertedApiKeyTemplate))
        )

        apiKeyTemplateService
          .createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)
          .asserting(_ shouldBe Right(insertedApiKeyTemplate))
      }
    }

    "ApiKeyTemplateRepository keeps returning Left containing ApiKeyTemplateAlreadyExistsError" should {

      "call UuidGenerator and ApiKeyTemplateRepository again until reaching max retries amount" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicTemplateId_1),
          IO.pure(publicTemplateId_2),
          IO.pure(publicTemplateId_3),
          IO.pure(publicTemplateId_4),
        )
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.pure(
          Left(apiKeyTemplateAlreadyExistsError)
        )

        for {
          _ <- apiKeyTemplateService.createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)

          _ = verify(uuidGenerator, times(4)).generateUuid
          _ = verify(apiKeyTemplateRepository).insert(eqTo(publicTenantId_1), eqTo(apiKeyTemplate))
          _ = verify(apiKeyTemplateRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(apiKeyTemplate.copy(publicTemplateId = publicTemplateId_2))
          )
          _ = verify(apiKeyTemplateRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(apiKeyTemplate.copy(publicTemplateId = publicTemplateId_3))
          )
          _ = verify(apiKeyTemplateRepository).insert(
            eqTo(publicTenantId_1),
            eqTo(apiKeyTemplate.copy(publicTemplateId = publicTemplateId_4))
          )
        } yield ()
      }

      "return successful IO containing Left with ApiKeyTemplateAlreadyExistsError" in {
        uuidGenerator.generateUuid returns (
          IO.pure(publicTemplateId_1),
          IO.pure(publicTemplateId_2),
          IO.pure(publicTemplateId_3),
          IO.pure(publicTemplateId_4),
        )
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.pure(
          Left(apiKeyTemplateAlreadyExistsError)
        )

        apiKeyTemplateService
          .createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)
          .asserting(_ shouldBe Left(apiKeyTemplateAlreadyExistsError))
      }
    }

    val testSqlException = new SQLException("Test SQL Exception")

    Seq(
      ReferencedTenantDoesNotExistError(publicTenantId_1),
      ApiKeyTemplateInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"ApiKeyTemplateRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "NOT call UuidGenerator or ApiKeyTemplateRepository again" in {
          uuidGenerator.generateUuid returns IO.pure(publicTemplateId_1)
          apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.pure(
            Left(insertionError)
          )

          for {
            _ <- apiKeyTemplateService.createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)

            _ = verify(uuidGenerator).generateUuid
            _ = verify(apiKeyTemplateRepository).insert(eqTo(publicTenantId_1), eqTo(apiKeyTemplate))
          } yield ()
        }

        "return Left containing this error" in {
          uuidGenerator.generateUuid returns IO.pure(publicTemplateId_1)
          apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.pure(
            Left(insertionError)
          )

          apiKeyTemplateService
            .createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)
            .asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplateRepository returns failed IO" should {

      "NOT call UuidGenerator or ApiKeyTemplateRepository again" in {
        uuidGenerator.generateUuid returns IO.pure(publicTemplateId_1)
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyTemplateService.createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest).attempt

          _ = verify(uuidGenerator).generateUuid
          _ = verify(apiKeyTemplateRepository).insert(eqTo(publicTenantId_1), eqTo(apiKeyTemplate))
        } yield ()
      }

      "return failed IO containing this exception" in {
        uuidGenerator.generateUuid returns IO.pure(publicTemplateId_1)
        apiKeyTemplateRepository.insert(any[UUID], any[ApiKeyTemplate]) returns IO.raiseError(testException)

        apiKeyTemplateService
          .createApiKeyTemplate(publicTenantId_1, createApiKeyTemplateRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on updateApiKeyTemplate" should {

    val updateApiKeyTemplateRequest = UpdateApiKeyTemplateRequest(
      name = apiKeyTemplateNameUpdated,
      description = apiKeyTemplateDescriptionUpdated,
      isDefault = true,
      apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
    )
    val apiKeyTemplateUpdate: ApiKeyTemplateUpdate = ApiKeyTemplateUpdate(
      publicTemplateId = publicTemplateId_1,
      name = apiKeyTemplateNameUpdated,
      description = apiKeyTemplateDescriptionUpdated,
      isDefault = true,
      apiKeyMaxExpiryPeriod = apiKeyMaxExpiryPeriodUpdated
    )

    "call ApiKeyTemplateRepository" in {
      apiKeyTemplateRepository.update(any[ApiKeyTemplateUpdate]) returns IO.pure(Right(apiKeyTemplateUpdated))

      for {
        _ <- apiKeyTemplateService.updateApiKeyTemplate(publicTemplateId_1, updateApiKeyTemplateRequest)

        _ = verify(apiKeyTemplateRepository).update(eqTo(apiKeyTemplateUpdate))
      } yield ()
    }

    "return value returned by ApiKeyTemplateRepository" when {

      "ApiKeyTemplateRepository returns Right" in {
        apiKeyTemplateRepository.update(any[ApiKeyTemplateUpdate]) returns IO.pure(Right(apiKeyTemplateUpdated))

        apiKeyTemplateService
          .updateApiKeyTemplate(publicTemplateId_1, updateApiKeyTemplateRequest)
          .asserting(_ shouldBe Right(apiKeyTemplateUpdated))
      }

      "ApiKeyTemplateRepository returns Left" in {
        apiKeyTemplateRepository.update(any[ApiKeyTemplateUpdate]) returns IO.pure(
          Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1))
        )

        apiKeyTemplateService
          .updateApiKeyTemplate(publicTemplateId_1, updateApiKeyTemplateRequest)
          .asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }
    }

    "return failed IO" when {
      "ApiKeyTemplateRepository returns failed IO" in {
        apiKeyTemplateRepository.update(any[ApiKeyTemplateUpdate]) returns IO.raiseError(testException)

        apiKeyTemplateService
          .updateApiKeyTemplate(publicTemplateId_1, updateApiKeyTemplateRequest)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on deleteApiKeyTemplate" should {

    "call ApiKeyTemplateRepository" in {
      apiKeyTemplateRepository.delete(any[UUID]) returns IO.pure(Right(apiKeyTemplate_1))

      for {
        _ <- apiKeyTemplateService.deleteApiKeyTemplate(publicTemplateId_1)

        _ = verify(apiKeyTemplateRepository).delete(eqTo(publicTemplateId_1))
      } yield ()
    }

    "return value returned by ApiKeyTemplateRepository" when {

      "ApiKeyTemplateRepository returns Right" in {
        apiKeyTemplateRepository.delete(any[UUID]) returns IO.pure(Right(apiKeyTemplate_1))

        apiKeyTemplateService
          .deleteApiKeyTemplate(publicTemplateId_1)
          .asserting(_ shouldBe Right(apiKeyTemplate_1))
      }

      "ApiKeyTemplateRepository returns Left" in {
        apiKeyTemplateRepository.delete(any[UUID]) returns IO.pure(
          Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1))
        )

        apiKeyTemplateService
          .deleteApiKeyTemplate(publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyTemplateNotFoundError(publicTemplateIdStr_1)))
      }
    }

    "return failed IO" when {
      "ApiKeyTemplateRepository returns failed IO" in {
        apiKeyTemplateRepository.delete(any[UUID]) returns IO.raiseError(testException)

        apiKeyTemplateService
          .deleteApiKeyTemplate(publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on getBy(:apiKeyTemplateId)" should {

    "call ApiKeyTemplateRepository" in {
      apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))

      for {
        _ <- apiKeyTemplateService.getBy(publicTemplateId_1)

        _ = verify(apiKeyTemplateRepository).getBy(eqTo(publicTemplateId_1))
      } yield ()
    }

    "return the value returned by ApiKeyTemplateRepository" when {

      "ApiKeyTemplateRepository returns empty Option" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(None)

        apiKeyTemplateService.getBy(publicTemplateId_1).asserting(_ shouldBe None)
      }

      "ApiKeyTemplateRepository returns non-empty Option" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.pure(Some(apiKeyTemplate_1))

        apiKeyTemplateService.getBy(publicTemplateId_1).asserting(_ shouldBe Some(apiKeyTemplate_1))
      }
    }

    "return failed IO" when {
      "ApiKeyTemplateRepository returns failed IO" in {
        apiKeyTemplateRepository.getBy(any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        apiKeyTemplateService.getBy(publicTemplateId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on getAllForTenant" should {

    "call ApiKeyTemplateRepository" in {
      apiKeyTemplateRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

      for {
        _ <- apiKeyTemplateService.getAllForTenant(publicTenantId_1)

        _ = verify(apiKeyTemplateRepository).getAllForTenant(eqTo(publicTenantId_1))
      } yield ()
    }

    "return the value returned by ApiKeyTemplateRepository" when {

      "ApiKeyTemplateRepository returns empty List" in {
        apiKeyTemplateRepository.getAllForTenant(any[TenantId]) returns IO.pure(List.empty)

        apiKeyTemplateService.getAllForTenant(publicTenantId_1).asserting(_ shouldBe List.empty[ApiKeyTemplate])
      }

      "ApiKeyTemplateRepository returns non-empty List" in {
        apiKeyTemplateRepository.getAllForTenant(any[TenantId]) returns IO.pure(
          List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
        )

        apiKeyTemplateService
          .getAllForTenant(publicTenantId_1)
          .asserting(_ shouldBe List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3))
      }
    }

    "return failed IO" when {
      "ApiKeyTemplateRepository returns failed IO" in {
        apiKeyTemplateRepository.getAllForTenant(any[TenantId]) returns IO.raiseError(testException)

        apiKeyTemplateService.getAllForTenant(publicTenantId_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on getAllForUser" when {

    "everything works correctly" should {

      "call UserRepository and ApiKeyTemplateRepository" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Option(user_1))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(List.empty)

        for {
          _ <- apiKeyTemplateService.getAllForUser(publicTenantId_1, publicUserId_1)

          _ = verify(userRepository).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(apiKeyTemplateRepository).getAllForUser(eqTo(publicTenantId_1), eqTo(publicUserId_1))
        } yield ()
      }

      "return the value returns by ApiKeyTemplateRepository" when {

        "ApiKeyTemplateRepository returns empty List" in {
          userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Option(user_1))
          apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(List.empty)

          apiKeyTemplateService
            .getAllForUser(publicTenantId_1, publicUserId_1)
            .asserting(_ shouldBe Right(List.empty))
        }

        "ApiKeyTemplateRepository returns non-empty List" in {
          userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Option(user_1))
          apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]) returns IO.pure(
            List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)
          )

          apiKeyTemplateService
            .getAllForUser(publicTenantId_1, publicUserId_1)
            .asserting(_ shouldBe Right(List(apiKeyTemplate_1, apiKeyTemplate_2, apiKeyTemplate_3)))
        }
      }
    }

    "UserRepository returns empty Option" should {

      "NOT call ApiKeyTemplateRepository" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Option.empty)

        for {
          _ <- apiKeyTemplateService.getAllForUser(publicTenantId_1, publicUserId_1)

          _ = verifyZeroInteractions(apiKeyTemplateRepository)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Option.empty)

        apiKeyTemplateService
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1)))
      }
    }

    "ApiKeyTemplateRepository returns failed IO" should {
      "return failed IO containing the same exception" in {
        userRepository.getBy(any[TenantId], any[UserId]) returns IO.pure(Option(user_1))
        apiKeyTemplateRepository.getAllForUser(any[TenantId], any[UserId]) returns IO.raiseError(testException)

        apiKeyTemplateService
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on associatePermissionsWithApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsRepository" in {
        apiKeyTemplatesPermissionsRepository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(
          ().asRight
        )

        for {
          _ <- apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          _ = verify(apiKeyTemplatesPermissionsRepository).insertMany(
            eqTo(publicTemplateId_1),
            eqTo(inputPublicPermissionIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesPermissionsRepository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(
          ().asRight
        )

        val result =
          apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    apiKeyTemplatesPermissionsInsertionErrors.foreach { insertionError =>
      s"ApiKeyTemplatesPermissionsRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesPermissionsRepository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO
            .pure(insertionError.asLeft)

          val result =
            apiKeyTemplateService.associatePermissionsWithApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesPermissionsRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsRepository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO
          .raiseError(testException)

        val result = apiKeyTemplateService
          .associatePermissionsWithApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)
          .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on removePermissionsFromApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsRepository" in {
        apiKeyTemplatesPermissionsRepository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(
          ().asRight
        )

        for {
          _ <- apiKeyTemplateService.removePermissionsFromApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)

          _ = verify(apiKeyTemplatesPermissionsRepository).deleteMany(
            eqTo(publicTemplateId_1),
            eqTo(inputPublicPermissionIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesPermissionsRepository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(
          ().asRight
        )

        val result =
          apiKeyTemplateService.removePermissionsFromApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    val allErrors = apiKeyTemplatesPermissionsInsertionErrors :+ ApiKeyTemplatesPermissionsNotFoundError(
      List(
        ApiKeyTemplatesPermissionsEntity.Write(101L, 102L),
        ApiKeyTemplatesPermissionsEntity.Write(201L, 202L),
        ApiKeyTemplatesPermissionsEntity.Write(301L, 302L)
      )
    )

    allErrors.foreach { insertionError =>
      s"ApiKeyTemplatesPermissionsRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesPermissionsRepository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO
            .pure(insertionError.asLeft)

          val result =
            apiKeyTemplateService.removePermissionsFromApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesPermissionsRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsRepository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO
          .raiseError(testException)

        val result = apiKeyTemplateService
          .removePermissionsFromApiKeyTemplate(publicTemplateId_1, inputPublicPermissionIds)
          .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateService on associateUsersWithApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesUsersRepository" in {
        apiKeyTemplatesUsersRepository.insertManyUsers(any[TenantId], any[ApiKeyTemplateId], any[List[UserId]]) returns IO
          .pure(
            ().asRight
          )

        for {
          _ <- apiKeyTemplateService.associateUsersWithApiKeyTemplate(
            publicTenantId_1,
            publicTemplateId_1,
            inputPublicUserIds
          )

          _ = verify(apiKeyTemplatesUsersRepository).insertManyUsers(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1),
            eqTo(inputPublicUserIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesUsersRepository.insertManyUsers(any[TenantId], any[ApiKeyTemplateId], any[List[UserId]]) returns IO
          .pure(
            ().asRight
          )

        val result =
          apiKeyTemplateService.associateUsersWithApiKeyTemplate(
            publicTenantId_1,
            publicTemplateId_1,
            inputPublicUserIds
          )

        result.asserting(_ shouldBe Right(()))
      }
    }

    Seq(
      ApiKeyTemplatesUsersAlreadyExistsError(101L, 202L),
      ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1),
      ApiKeyTemplatesUsersInsertionError.ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1),
      ApiKeyTemplatesUsersInsertionErrorImpl(testSqlException)
    ).foreach { insertionError =>
      s"ApiKeyTemplatesUsersRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesUsersRepository.insertManyUsers(any[TenantId], any[ApiKeyTemplateId], any[List[UserId]]) returns IO
            .pure(insertionError.asLeft)

          val result =
            apiKeyTemplateService.associateUsersWithApiKeyTemplate(
              publicTenantId_1,
              publicTemplateId_1,
              inputPublicUserIds
            )

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesUsersRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesUsersRepository.insertManyUsers(any[TenantId], any[ApiKeyTemplateId], any[List[UserId]]) returns IO
          .raiseError(testException)

        val result = apiKeyTemplateService
          .associateUsersWithApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)
          .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
