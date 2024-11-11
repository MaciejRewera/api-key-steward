package apikeysteward.services

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData.publicTemplateId_1
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyTemplatesPermissionsDbError.ApiKeyTemplatesPermissionsNotFoundError
import apikeysteward.repositories.ApiKeyTemplatesPermissionsRepository
import apikeysteward.repositories.db.entity.ApiKeyTemplatesPermissionsEntity
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

class ApiKeyTemplatesPermissionsServiceSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with BeforeAndAfterEach {

  private val repository = mock[ApiKeyTemplatesPermissionsRepository]

  private val apiKeyTemplatesPermissionsService = new ApiKeyTemplatesPermissionsService(repository)

  override def beforeEach(): Unit =
    reset(repository)

  private val testException = new RuntimeException("Test Exception")
  private val testSqlException = new SQLException("Test SQL Exception")

  private val insertionErrors = Seq(
    ApiKeyTemplatesPermissionsAlreadyExistsError(101L, 102L),
    ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1),
    ReferencedPermissionDoesNotExistError(publicPermissionId_1),
    ApiKeyTemplatesPermissionsInsertionErrorImpl(testSqlException)
  )

  private val inputPublicPermissionIds = List(publicPermissionId_1, publicPermissionId_2, publicPermissionId_3)

  "ApiKeyTemplatesPermissionsService on associatePermissionsWithApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsRepository" in {
        repository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(().asRight)

        for {
          _ <- apiKeyTemplatesPermissionsService.associatePermissionsWithApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          _ = verify(repository).insertMany(eqTo(publicTemplateId_1), eqTo(inputPublicPermissionIds))
        } yield ()
      }

      "return Right containing Unit value" in {
        repository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(().asRight)

        val result = apiKeyTemplatesPermissionsService.associatePermissionsWithApiKeyTemplate(
          publicTemplateId_1,
          inputPublicPermissionIds
        )

        result.asserting(_ shouldBe Right(()))
      }
    }

    insertionErrors.foreach { insertionError =>
      s"ApiKeyTemplatesPermissionsRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          repository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(insertionError.asLeft)

          val result = apiKeyTemplatesPermissionsService.associatePermissionsWithApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesPermissionsRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        repository.insertMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.raiseError(testException)

        val result = apiKeyTemplatesPermissionsService
          .associatePermissionsWithApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )
          .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplatesPermissionsService on removePermissionsFromApiKeyTemplate" when {

    "everything works correctly" should {

      "call ApiKeyTemplatesPermissionsRepository" in {
        repository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(().asRight)

        for {
          _ <- apiKeyTemplatesPermissionsService.removePermissionsFromApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          _ = verify(repository).deleteMany(eqTo(publicTemplateId_1), eqTo(inputPublicPermissionIds))
        } yield ()
      }

      "return Right containing Unit value" in {
        repository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(().asRight)

        val result = apiKeyTemplatesPermissionsService.removePermissionsFromApiKeyTemplate(
          publicTemplateId_1,
          inputPublicPermissionIds
        )

        result.asserting(_ shouldBe Right(()))
      }
    }

    val allErrors = insertionErrors :+ ApiKeyTemplatesPermissionsNotFoundError(
      List(
        ApiKeyTemplatesPermissionsEntity.Write(101L, 102L),
        ApiKeyTemplatesPermissionsEntity.Write(201L, 202L),
        ApiKeyTemplatesPermissionsEntity.Write(301L, 302L)
      )
    )

    allErrors.foreach { insertionError =>
      s"ApiKeyTemplatesPermissionsRepository returns Left containing ${insertionError.getClass.getSimpleName}" should {

        "return Left containing this error" in {
          repository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.pure(insertionError.asLeft)

          val result = apiKeyTemplatesPermissionsService.removePermissionsFromApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )

          result.asserting(_ shouldBe Left(insertionError))
        }
      }
    }

    "ApiKeyTemplatesPermissionsRepository returns failed IO" should {
      "return failed IO containing this exception" in {
        repository.deleteMany(any[ApiKeyTemplateId], any[List[PermissionId]]) returns IO.raiseError(testException)

        val result = apiKeyTemplatesPermissionsService
          .removePermissionsFromApiKeyTemplate(
            publicTemplateId_1,
            inputPublicPermissionIds
          )
          .attempt

        result.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyTemplatesPermissionsService on getAllPermissionsForApiKeyTemplate" should {

    "call ApiKeyTemplatesPermissionsRepository" in {
      repository.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns IO.pure(
        List(permission_1, permission_2, permission_3)
      )

      for {
        _ <- apiKeyTemplatesPermissionsService.getAllPermissionsForApiKeyTemplate(publicTemplateId_1)

        _ = verify(repository).getAllPermissionsForTemplate(eqTo(publicTemplateId_1))
      } yield ()
    }

    "return the value returned by ApiKeyTemplatesPermissionsRepository" when {

      "ApiKeyTemplatesPermissionsRepository returns empty List" in {
        repository.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns IO.pure(List.empty[Permission])

        apiKeyTemplatesPermissionsService
          .getAllPermissionsForApiKeyTemplate(publicTemplateId_1)
          .asserting(_ shouldBe List.empty[Permission])
      }

      "ApiKeyTemplatesPermissionsRepository returns non-empty List" in {
        repository.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns IO.pure(
          List(permission_1, permission_2, permission_3)
        )

        apiKeyTemplatesPermissionsService
          .getAllPermissionsForApiKeyTemplate(publicTemplateId_1)
          .asserting(_ shouldBe List(permission_1, permission_2, permission_3))
      }
    }

    "return failed IO" when {
      "ApiKeyTemplatesPermissionsRepository returns failed IO" in {
        repository.getAllPermissionsForTemplate(any[ApiKeyTemplateId]) returns IO.raiseError(testException)

        apiKeyTemplatesPermissionsService
          .getAllPermissionsForApiKeyTemplate(publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }
  }

}
