package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.{
  publicTemplateId_1,
  publicTemplateId_2,
  publicTemplateId_3,
  templateDbId_1,
  templateDbId_2,
  templateDbId_3
}
import apikeysteward.base.testdata.ApiKeyTemplatesUsersTestData.{
  apiKeyTemplatesUsersEntityWrite_1_1,
  apiKeyTemplatesUsersEntityWrite_1_2,
  apiKeyTemplatesUsersEntityWrite_1_3
}
import apikeysteward.base.testdata.PermissionsTestData.{
  publicPermissionId_1,
  publicPermissionId_2,
  publicPermissionId_3
}
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1}
import apikeysteward.base.testdata.UsersTestData.{
  publicUserId_1,
  publicUserId_2,
  publicUserId_3,
  userDbId_1,
  userDbId_2,
  userDbId_3
}
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.ApiKeyTemplatesUsersInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesUsersDbError.{
  ApiKeyTemplatesUsersInsertionError,
  ApiKeyTemplatesUsersNotFoundError
}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories._
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesPermissionsEntity, ApiKeyTemplatesUsersEntity}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxEitherId
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException

class ApiKeyTemplateAssociationsServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val apiKeyTemplatesPermissionsRepository = mock[ApiKeyTemplatesPermissionsRepository]
  private val apiKeyTemplatesUsersRepository = mock[ApiKeyTemplatesUsersRepository]

  private val theService = new ApiKeyTemplateAssociationsService(
    apiKeyTemplatesPermissionsRepository,
    apiKeyTemplatesUsersRepository
  )

  override def beforeEach(): Unit =
    reset(apiKeyTemplatesPermissionsRepository, apiKeyTemplatesUsersRepository)

  private val testException = new RuntimeException("Test Exception")
  private val testSqlException = new SQLException("Test SQL Exception")

  private val apiKeyTemplatesPermissionsInsertionErrors = Seq(
    ApiKeyTemplatesPermissionsAlreadyExistsError(templateDbId_1, userDbId_1),
    ApiKeyTemplatesPermissionsInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1),
    ReferencedPermissionDoesNotExistError(publicPermissionId_1),
    ApiKeyTemplatesPermissionsInsertionErrorImpl(testSqlException)
  )

  private val apiKeyTemplatesUsersInsertionErrors = Seq(
    ApiKeyTemplatesUsersAlreadyExistsError(templateDbId_1, userDbId_2),
    ApiKeyTemplatesUsersInsertionError.ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1),
    ApiKeyTemplatesUsersInsertionError.ReferencedUserDoesNotExistError(publicUserId_1, publicTenantId_1),
    ApiKeyTemplatesUsersInsertionErrorImpl(testSqlException)
  )

  private val inputPublicPermissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)
  private val inputPublicUserIds = List(publicUserId_1, publicUserId_2, publicUserId_3)
  private val inputPublicApiKeyTemplateIds = List(publicTemplateId_1, publicTemplateId_2, publicTemplateId_3)

  "ApiKeyTemplateAssociationsService on associatePermissionsWithApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsRepository" in {
        apiKeyTemplatesPermissionsRepository.insertMany(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          ().asRight
        )

        for {
          _ <- theService.associatePermissionsWithApiKeyTemplate(
            publicTenantId_1,
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          _ = verify(apiKeyTemplatesPermissionsRepository).insertMany(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1),
            eqTo(inputPublicPermissionIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesPermissionsRepository.insertMany(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          ().asRight
        )

        val result = theService.associatePermissionsWithApiKeyTemplate(
          publicTenantId_1,
          publicTemplateId_1,
          inputPublicPermissionIds
        )

        result.asserting(_ shouldBe Right(()))
      }
    }

    apiKeyTemplatesPermissionsInsertionErrors.foreach { insertionError =>
      s"ApiKeyTemplatesPermissionsRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesPermissionsRepository.insertMany(
            any[TenantId],
            any[ApiKeyTemplateId],
            any[List[PermissionId]]
          ) returns IO
            .pure(insertionError.asLeft)

          val result = theService.associatePermissionsWithApiKeyTemplate(
            publicTenantId_1,
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesPermissionsRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsRepository.insertMany(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO
          .raiseError(testException)

        val result =
          theService
            .associatePermissionsWithApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicPermissionIds)
            .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateAssociationsService on removePermissionsFromApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsRepository" in {
        apiKeyTemplatesPermissionsRepository.deleteMany(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          ().asRight
        )

        for {
          _ <- theService.removePermissionsFromApiKeyTemplate(
            publicTenantId_1,
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          _ = verify(apiKeyTemplatesPermissionsRepository).deleteMany(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1),
            eqTo(inputPublicPermissionIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesPermissionsRepository.deleteMany(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO.pure(
          ().asRight
        )

        val result =
          theService.removePermissionsFromApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicPermissionIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    val allErrors = apiKeyTemplatesPermissionsInsertionErrors :+ ApiKeyTemplatesPermissionsNotFoundError(
      List(
        ApiKeyTemplatesPermissionsEntity.Write(tenantDbId_1, templateDbId_1, userDbId_1),
        ApiKeyTemplatesPermissionsEntity.Write(tenantDbId_1, templateDbId_2, userDbId_2),
        ApiKeyTemplatesPermissionsEntity.Write(tenantDbId_1, templateDbId_3, userDbId_3)
      )
    )

    allErrors.foreach { insertionError =>
      s"ApiKeyTemplatesPermissionsRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesPermissionsRepository.deleteMany(
            any[TenantId],
            any[ApiKeyTemplateId],
            any[List[PermissionId]]
          ) returns IO
            .pure(insertionError.asLeft)

          val result = theService.removePermissionsFromApiKeyTemplate(
            publicTenantId_1,
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesPermissionsRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesPermissionsRepository.deleteMany(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[PermissionId]]
        ) returns IO
          .raiseError(testException)

        val result =
          theService
            .removePermissionsFromApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicPermissionIds)
            .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateAssociationsService on associateUsersWithApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesUsersRepository" in {
        apiKeyTemplatesUsersRepository.insertManyUsers(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- theService.associateUsersWithApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          _ = verify(apiKeyTemplatesUsersRepository).insertManyUsers(
            eqTo(publicTenantId_1),
            eqTo(publicTemplateId_1),
            eqTo(inputPublicUserIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesUsersRepository.insertManyUsers(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.pure(().asRight)

        val result =
          theService.associateUsersWithApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    apiKeyTemplatesUsersInsertionErrors.foreach { insertionError =>
      s"ApiKeyTemplatesUsersRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesUsersRepository.insertManyUsers(
            any[TenantId],
            any[ApiKeyTemplateId],
            any[List[UserId]]
          ) returns IO.pure(insertionError.asLeft)

          val result =
            theService.associateUsersWithApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicUserIds)

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesUsersRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesUsersRepository.insertManyUsers(
          any[TenantId],
          any[ApiKeyTemplateId],
          any[List[UserId]]
        ) returns IO.raiseError(testException)

        val result =
          theService.associateUsersWithApiKeyTemplate(publicTenantId_1, publicTemplateId_1, inputPublicUserIds).attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateAssociationsService on associateApiKeyTemplatesWithUser" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesUsersRepository" in {
        apiKeyTemplatesUsersRepository.insertManyTemplates(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- theService.associateApiKeyTemplatesWithUser(
            publicTenantId_1,
            publicUserId_1,
            inputPublicApiKeyTemplateIds
          )

          _ = verify(apiKeyTemplatesUsersRepository).insertManyTemplates(
            eqTo(publicTenantId_1),
            eqTo(publicUserId_1),
            eqTo(inputPublicApiKeyTemplateIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesUsersRepository.insertManyTemplates(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        val result =
          theService.associateApiKeyTemplatesWithUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    apiKeyTemplatesUsersInsertionErrors.foreach { insertionError =>
      s"ApiKeyTemplatesUsersRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesUsersRepository.insertManyTemplates(
            any[TenantId],
            any[UserId],
            any[List[ApiKeyTemplateId]]
          ) returns IO.pure(insertionError.asLeft)

          val result =
            theService.associateApiKeyTemplatesWithUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesUsersRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesUsersRepository.insertManyTemplates(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.raiseError(testException)

        val result =
          theService
            .associateApiKeyTemplatesWithUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)
            .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplateAssociationsService on removeApiKeyTemplatesFromUser" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesUsersRepository" in {
        apiKeyTemplatesUsersRepository.deleteManyTemplates(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        for {
          _ <- theService.removeApiKeyTemplatesFromUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)

          _ = verify(apiKeyTemplatesUsersRepository).deleteManyTemplates(
            eqTo(publicTenantId_1),
            eqTo(publicUserId_1),
            eqTo(inputPublicApiKeyTemplateIds)
          )
        } yield ()
      }

      "return Right containing Unit value" in {
        apiKeyTemplatesUsersRepository.deleteManyTemplates(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.pure(().asRight)

        val result =
          theService.removeApiKeyTemplatesFromUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)

        result.asserting(_ shouldBe Right(()))
      }
    }

    val allErrors = apiKeyTemplatesUsersInsertionErrors :+ ApiKeyTemplatesUsersNotFoundError(
      List(
        apiKeyTemplatesUsersEntityWrite_1_1,
        apiKeyTemplatesUsersEntityWrite_1_2,
        apiKeyTemplatesUsersEntityWrite_1_3
      )
    )

    allErrors.foreach { insertionError =>
      s"ApiKeyTemplatesUsersRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          apiKeyTemplatesUsersRepository.deleteManyTemplates(
            any[TenantId],
            any[UserId],
            any[List[ApiKeyTemplateId]]
          ) returns IO.pure(insertionError.asLeft)

          val result =
            theService.removeApiKeyTemplatesFromUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesUsersRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        apiKeyTemplatesUsersRepository.deleteManyTemplates(
          any[TenantId],
          any[UserId],
          any[List[ApiKeyTemplateId]]
        ) returns IO.raiseError(testException)

        val result = theService
          .removeApiKeyTemplatesFromUser(publicTenantId_1, publicUserId_1, inputPublicApiKeyTemplateIds)
          .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }
}
