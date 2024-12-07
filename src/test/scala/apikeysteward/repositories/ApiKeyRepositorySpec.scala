package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1, tenantEntityRead_1}
import apikeysteward.base.testdata.UsersTestData.publicUserId_1
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError._
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.{ApiKey, HashedApiKey}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyEntity, TenantEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDb, TenantDb}
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito.verify
import org.mockito.MockitoSugar._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.util.UUID

class ApiKeyRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val uuidGenerator = mock[UuidGenerator]
  private val apiKeyDb = mock[ApiKeyDb]
  private val tenantDb = mock[TenantDb]
  private val apiKeyDataDb = mock[ApiKeyDataDb]
  private val secureHashGenerator = mock[SecureHashGenerator]

  private val apiKeyRepository =
    new ApiKeyRepository(uuidGenerator, tenantDb, apiKeyDb, apiKeyDataDb, secureHashGenerator)(noopTransactor)

  override def beforeEach(): Unit =
    reset(uuidGenerator, tenantDb, apiKeyDb, apiKeyDataDb, secureHashGenerator)

  private val tenantEntityReadWrapped: doobie.ConnectionIO[Option[TenantEntity.Read]] =
    Option(tenantEntityRead_1).pure[doobie.ConnectionIO]

  private val apiKeyIdAlreadyExistsErrorWrapped =
    ApiKeyIdAlreadyExistsError
      .asInstanceOf[ApiKeyInsertionError]
      .asLeft[ApiKeyDataEntity.Read]
      .pure[doobie.ConnectionIO]

  private val publicKeyIdAlreadyExistsErrorWrapped =
    PublicKeyIdAlreadyExistsError
      .asInstanceOf[ApiKeyInsertionError]
      .asLeft[ApiKeyDataEntity.Read]
      .pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")

  "ApiKeyRepository on insert" when {

    val apiKeyEntityReadWrapped = apiKeyEntityRead_1.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]
    val apiKeyDataEntityReadWrapped = apiKeyDataEntityRead_1.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]

    val apiKeyAlreadyExistsErrorWrapped =
      ApiKeyAlreadyExistsError.asInstanceOf[ApiKeyInsertionError].asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call SecureHashGenerator, UuidGenerator, TenantDb, ApiKeyDb and ApiKeyDataDb once, providing correct entities" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped

        val expectedEntityWrite =
          ApiKeyEntity.Write(id = apiKeyDbId_1, tenantId = tenantDbId_1, apiKey = hashedApiKey_1.value)

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1)

          _ = verify(secureHashGenerator).generateHashFor(eqTo(apiKey_1))
          _ = verify(uuidGenerator, times(2)).generateUuid
          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
          _ = verify(apiKeyDb).insert(eqTo(expectedEntityWrite))
          _ = verify(apiKeyDataDb).insert(eqTo(apiKeyDataEntityWrite_1))
        } yield ()
      }

      "return Right containing ApiKeyData" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped

        apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1).asserting(_ shouldBe Right(apiKeyData_1))
      }
    }

    "SecureHashGenerator returns failed IO" should {

      "NOT call UuidGenerator, TenantDb, ApiKeyDb or ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1).attempt

          _ = verifyZeroInteractions(uuidGenerator, tenantDb, apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantDb, ApiKeyDb or ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1).attempt

          _ = verifyZeroInteractions(tenantDb, apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call ApiKeyDb or ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1)

          _ = verifyZeroInteractions(apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistErrorImpl" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call ApiKeyDb or ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1).attempt

          _ = verifyZeroInteractions(apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDb returns Left containing ApiKeyAlreadyExistsError" should {

      "NOT call ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1)

          _ = verifyZeroInteractions(apiKeyDataDb)
        } yield ()
      }

      "return Left containing ApiKeyAlreadyExistsError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
          .asserting(_ shouldBe Left(ApiKeyAlreadyExistsError))
      }
    }

    "ApiKeyDb returns different exception" should {

      "NOT call ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1).attempt

          _ = verifyZeroInteractions(apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDb returns Right containing ApiKeyEntity.Read" when {

      def fixture[T](test: => IO[T]): IO[T] = IO {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
      }.flatMap(_ => test)

      "ApiKeyDataDb returns Left containing ApiKeyIdAlreadyExistsError" should {
        "return Left containing ApiKeyIdAlreadyExistsError" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
            .asserting(_ shouldBe Left(ApiKeyIdAlreadyExistsError))
        }
      }

      "ApiKeyDataDb returns Left containing PublicKeyIdAlreadyExistsError" should {
        "return Left containing PublicKeyIdAlreadyExistsError" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
            .asserting(_ shouldBe Left(PublicKeyIdAlreadyExistsError))
        }
      }

      "ApiKeyDataDb returns different exception" should {
        "return failed IO containing this exception" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns testException
            .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]]

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1)
            .attempt
            .asserting(_ shouldBe Left(testException))
        }
      }
    }
  }

  "ApiKeyRepository on update" when {

    val apiKeyDataEntityReadWrapped = Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
    val apiKeyDataEntityReadWrappedUpdated = apiKeyDataEntityRead_1
      .copy(name = nameUpdated, description = descriptionUpdated, updatedAt = nowInstant)
      .asRight[ApiKeyDbError]
      .pure[doobie.ConnectionIO]

    "everything works correctly" when {

      "ApiKeyData under update has scopes" should {

        "call ApiKeyDataDb, providing correct entities" in {
          tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
          apiKeyDataDb.update(any[TenantId], any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated

          val expectedApiKeyDataEntityUpdate = ApiKeyDataEntity.Update(
            publicKeyId = publicKeyId_1.toString,
            name = nameUpdated,
            description = descriptionUpdated
          )

          for {
            _ <- apiKeyRepository.update(publicTenantId_1, apiKeyDataUpdate_1)

            _ = verify(apiKeyDataDb).update(eqTo(publicTenantId_1), eqTo(expectedApiKeyDataEntityUpdate))
          } yield ()
        }

        "return Right containing ApiKeyData" in {
          tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
          apiKeyDataDb.update(any[TenantId], any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated

          val expectedApiKeyData = apiKeyData_1.copy(name = nameUpdated, description = descriptionUpdated)

          apiKeyRepository.update(publicTenantId_1, apiKeyDataUpdate_1).asserting(_ shouldBe Right(expectedApiKeyData))
        }
      }
    }

    "ApiKeyDataDb returns Left containing ApiKeyDataNotFoundError" should {
      "return Left containing ApiKeyDataNotFoundError" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[TenantId], any[ApiKeyDataEntity.Update]) returns ApiKeyDataNotFoundError(publicKeyId_1)
          .asInstanceOf[ApiKeyDbError]
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .update(publicTenantId_1, apiKeyDataUpdate_1)
          .asserting(
            _ shouldBe Left(ApiKeyDataNotFoundError(apiKeyDataUpdate_1.publicKeyId))
          )
      }
    }

    "ApiKeyDataDb returns exception" should {
      "return failed IO containing the same exception" in {
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[TenantId], any[ApiKeyDataEntity.Update]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyDbError, ApiKeyDataEntity.Read]]

        apiKeyRepository.update(publicTenantId_1, apiKeyDataUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyRepository on get(:apiKey)" when {

    "should always call SecureHashGenerator" in {
      secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

      for {
        _ <- apiKeyRepository.get(publicTenantId_1, apiKey_1).attempt

        _ = verify(secureHashGenerator).generateHashFor(eqTo(apiKey_1))
      } yield ()
    }

    "SecureHashGenerator returns failed IO" should {

      "NOT call ApiKeyDb or ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.get(publicTenantId_1, apiKey_1).attempt

          _ = verifyZeroInteractions(apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        apiKeyRepository
          .get(publicTenantId_1, apiKey_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "SecureHashGenerator returns IO containing HashedApiKey" when {

      "should call ApiKeyDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.getByApiKey(any[TenantId], any[HashedApiKey]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.get(publicTenantId_1, apiKey_1)

          _ = verify(apiKeyDb).getByApiKey(eqTo(publicTenantId_1), eqTo(hashedApiKey_1))
        } yield ()
      }

      "ApiKeyDb returns empty Option" should {

        "NOT call ApiKeyDataDb" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.getByApiKey(any[TenantId], any[HashedApiKey]) returns none[ApiKeyEntity.Read]
            .pure[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.get(publicTenantId_1, apiKey_1)

            _ = verifyZeroInteractions(apiKeyDataDb)
          } yield ()
        }

        "return empty Option" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.getByApiKey(any[TenantId], any[HashedApiKey]) returns none[ApiKeyEntity.Read]
            .pure[doobie.ConnectionIO]

          apiKeyRepository.get(publicTenantId_1, apiKey_1).asserting(_ shouldBe None)
        }
      }

      "ApiKeyDb returns ApiKeyEntity" when {

        "should always call ApiKeyDataDb" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.getByApiKey(any[TenantId], any[HashedApiKey]) returns Option(apiKeyEntityRead_1)
            .pure[doobie.ConnectionIO]
          apiKeyDataDb.getByApiKeyId(any[TenantId], any[UUID]) returns none[ApiKeyDataEntity.Read]
            .pure[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.get(publicTenantId_1, apiKey_1)

            _ = verify(apiKeyDataDb).getByApiKeyId(eqTo(publicTenantId_1), eqTo(apiKeyDbId_1))
          } yield ()
        }

        "ApiKeyDataDb returns empty Option" should {
          "return empty Option" in {
            secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
            apiKeyDb.getByApiKey(any[TenantId], any[HashedApiKey]) returns Option(apiKeyEntityRead_1)
              .pure[doobie.ConnectionIO]
            apiKeyDataDb.getByApiKeyId(any[TenantId], any[UUID]) returns none[ApiKeyDataEntity.Read]
              .pure[doobie.ConnectionIO]

            apiKeyRepository.get(publicTenantId_1, apiKey_1).asserting(_ shouldBe None)
          }
        }

        "ApiKeyDataDb returns ApiKeyDataEntity" when {
          "return ApiKeyData" in {
            secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
            apiKeyDb.getByApiKey(any[TenantId], any[HashedApiKey]) returns Option(apiKeyEntityRead_1)
              .pure[doobie.ConnectionIO]
            apiKeyDataDb.getByApiKeyId(any[TenantId], any[UUID]) returns Option(apiKeyDataEntityRead_1)
              .pure[doobie.ConnectionIO]

            apiKeyRepository.get(publicTenantId_1, apiKey_1).asserting(_ shouldBe Some(apiKeyData_1))
          }
        }
      }
    }
  }

  "ApiKeyRepository on get(:userId, :publicKeyId)" when {

    "should always call ApiKeyDataDb" in {
      apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
        .pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.get(publicTenantId_1, publicUserId_1, publicKeyId_1)

        _ = verify(apiKeyDataDb).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "should NOT call ApiKeyDb" in {
      apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
        .pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.get(publicTenantId_1, publicUserId_1, publicKeyId_1)

        _ = verifyZeroInteractions(apiKeyDb)
      } yield ()
    }

    "ApiKeyDataDb returns empty Option" should {
      "return empty Option" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .get(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Option.empty[ApiKeyDataEntity.Read])
      }
    }

    "ApiKeyDataDb returns ApiKeyDataEntity" when {
      "return Option containing ApiKeyData" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]

        apiKeyRepository.get(publicTenantId_1, publicUserId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
      }
    }
  }

  "ApiKeyRepository on getByPublicKeyId" when {

    "should always call ApiKeyDataDb" in {
      apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
        .pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.getByPublicKeyId(publicTenantId_1, publicKeyId_1)

        _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "should NOT call ApiKeyDb" in {
      apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
        .pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.getByPublicKeyId(publicTenantId_1, publicKeyId_1)

        _ = verifyZeroInteractions(apiKeyDb)
      } yield ()
    }

    "ApiKeyDataDb returns empty Option" should {
      "return empty Option" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .getByPublicKeyId(publicTenantId_1, publicKeyId_1)
          .asserting(_ shouldBe Option.empty[ApiKeyDataEntity.Read])
      }
    }

    "ApiKeyDataDb returns ApiKeyDataEntity" when {
      "return Option containing ApiKeyData" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]

        apiKeyRepository.getByPublicKeyId(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
      }
    }
  }

  "ApiKeyRepository on getAll(:userId)" when {

    "should always call ApiKeyDataDb" in {
      apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAllForUser(publicTenantId_1, publicUserId_1)

        _ = verify(apiKeyDataDb).getByUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
      } yield ()
    }

    "should NOT call ApiKeyDb" in {
      apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAllForUser(publicTenantId_1, publicUserId_1)

        _ = verifyZeroInteractions(apiKeyDb)
      } yield ()
    }

    "ApiKeyDataDb returns empty Stream" should {
      "return empty List" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns Stream.empty

        apiKeyRepository
          .getAllForUser(publicTenantId_1, publicUserId_1)
          .asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "ApiKeyDataDb returns elements in Stream" when {
      "return ApiKeyData with empty scopes fields" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns Stream(
          apiKeyDataEntityRead_1,
          apiKeyDataEntityRead_2
        )

        apiKeyRepository.getAllForUser(publicTenantId_1, publicUserId_1).asserting { result =>
          result.size shouldBe 2
          result should contain theSameElementsAs List(apiKeyData_1, apiKeyData_2)
        }
      }
    }
  }

  "ApiKeyRepository on delete(:userId, :publicKeyId)" when {

    val apiKeyEntityRead_1 = ApiKeyEntity.Read(apiKeyDbId_1, tenantDbId_1, nowInstant, nowInstant)

    "everything works correctly" when {

      def initMocks(): Unit = {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[TenantId], any[UUID]) returns apiKeyEntityRead_1
          .asRight[ApiKeyNotFoundError.type]
          .pure[doobie.ConnectionIO]
      }

      "call ApiKeyDb and ApiKeyDataDb" in {
        initMocks()

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1), eqTo(publicKeyId_1))
          _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
          _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))

        } yield ()
      }

      "return Right containing deleted ApiKeyData" in {
        initMocks()

        apiKeyRepository
          .delete(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Right(apiKeyData_1))
      }

    }

    "ApiKeyDataDb.getBy returns empty Option" should {

      "NOT call ApiKeyDb or ApiKeyDataDb.delete" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb, never).delete(any[TenantId], any[ApiKeyId])
          _ = verifyZeroInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFound" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDb" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1)

          _ = verifyZeroInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDb.delete returns Left containing ApiKeyNotFoundError" should {
      "return Left containing ApiKeyNotFoundError" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[TenantId], any[UUID]) returns ApiKeyNotFoundError
          .asLeft[ApiKeyEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }
    }
  }

  "ApiKeyRepository on delete(:publicKeyId)" when {

    val apiKeyEntityRead_1 = ApiKeyEntity.Read(apiKeyDbId_1, tenantDbId_1, nowInstant, nowInstant)

    "everything works correctly" when {

      def initMocks(): Unit = {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[TenantId], any[UUID]) returns apiKeyEntityRead_1
          .asRight[ApiKeyNotFoundError.type]
          .pure[doobie.ConnectionIO]
      }

      "call ApiKeyDb and ApiKeyDataDb" in {
        initMocks()

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
          _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
          _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
        } yield ()
      }

      "return Right containing deleted ApiKeyData" in {
        initMocks()

        apiKeyRepository.delete(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
      }
    }

    "ApiKeyDataDb.getByPublicKeyId returns empty Option" should {

      "NOT call ApiKeyDb or ApiKeyDataDb.delete" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb, never).delete(any[TenantId], any[ApiKeyId])
          _ = verifyZeroInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFound" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicTenantId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDb" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1)

          _ = verifyZeroInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicTenantId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDb.delete returns Left containing ApiKeyNotFoundError" should {
      "return Left containing ApiKeyNotFoundError" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[TenantId], any[UUID]) returns ApiKeyNotFoundError
          .asLeft[ApiKeyEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository.delete(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }
    }
  }

}
