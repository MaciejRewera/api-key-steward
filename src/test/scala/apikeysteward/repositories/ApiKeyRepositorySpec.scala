package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.TestData._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError
import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.RepositoryErrors.ApiKeyDbError._
import apikeysteward.model.{ApiKey, HashedApiKey}
import apikeysteward.repositories.db.entity.{ApiKeyDataEntity, ApiKeyDataScopesEntity, ApiKeyEntity, ScopeEntity}
import apikeysteward.repositories.db.{ApiKeyDataDb, ApiKeyDataScopesDb, ApiKeyDb, ScopeDb}
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
import java.util.concurrent.TimeUnit

class ApiKeyRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val apiKeyDb = mock[ApiKeyDb]
  private val apiKeyDataDb = mock[ApiKeyDataDb]
  private val scopeDb = mock[ScopeDb]
  private val apiKeyDataScopesDb = mock[ApiKeyDataScopesDb]
  private val secureHashGenerator = mock[SecureHashGenerator]

  private val apiKeyRepository =
    new ApiKeyRepository(apiKeyDb, apiKeyDataDb, scopeDb, apiKeyDataScopesDb, secureHashGenerator)(noopTransactor)

  override def beforeEach(): Unit =
    reset(apiKeyDb, apiKeyDataDb, scopeDb, apiKeyDataScopesDb, secureHashGenerator)

  private val apiKeyEntityRead = ApiKeyEntity.Read(
    id = 13L,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  private val apiKeyDataEntityRead_1 = ApiKeyDataEntity.Read(
    id = 1L,
    apiKeyId = 2L,
    publicKeyId = publicKeyId_1.toString,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )
  private val apiKeyDataEntityRead_2 = ApiKeyDataEntity.Read(
    id = 2L,
    apiKeyId = 3L,
    publicKeyId = publicKeyId_2.toString,
    name = name,
    description = description,
    userId = userId_1,
    expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit),
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  private val scopeEntities_1 = scopes_1.zipWithIndex.map { case (scope, idx) => ScopeEntity.Read(idx, scope) }

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

  private val scopeEntitiesReadWrapped: Stream[doobie.ConnectionIO, ScopeEntity.Read] =
    Stream.emits(scopeEntities_1).covary[doobie.ConnectionIO]

  private val apiKeyDataScopesInsertResultWrapped = scopes_1.size.pure[doobie.ConnectionIO]

  private val testException = new RuntimeException("Test Exception")

  "ApiKeyRepository on insert" when {

    val apiKeyEntityReadWrapped = apiKeyEntityRead.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]
    val apiKeyDataEntityReadWrapped = apiKeyDataEntityRead_1.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]

    val apiKeyAlreadyExistsErrorWrapped =
      ApiKeyAlreadyExistsError.asInstanceOf[ApiKeyInsertionError].asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

    "everything works correctly" should {

      "call SecureHashGenerator, ApiKeyDb, ApiKeyDataDb, ScopeDb and ApiKeyDataScopesDb once, providing correct entities" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
        scopeDb.insertMany(any[List[ScopeEntity.Write]]) returns scopeEntitiesReadWrapped
        apiKeyDataScopesDb.insertMany(
          any[List[ApiKeyDataScopesEntity.Write]]
        ) returns apiKeyDataScopesInsertResultWrapped

        val expectedEntityWrite = ApiKeyEntity.Write(hashedApiKey_1.value)
        val expectedDataEntityWrite = ApiKeyDataEntity.Write(
          apiKeyId = apiKeyEntityRead.id,
          publicKeyId = publicKeyId_1.toString,
          name = name,
          description = description,
          userId = userId_1,
          expiresAt = nowInstant.plus(ttlMinutes, TimeUnit.MINUTES.toChronoUnit)
        )
        val expectedScopeEntitiesWrite = scopes_1.map(ScopeEntity.Write)
        val expectedApiKeyDataScopesEntitiesWrite = scopeEntities_1.map(_.id).map { scopeId =>
          ApiKeyDataScopesEntity.Write(apiKeyDataId = apiKeyDataEntityRead_1.id, scopeId = scopeId)
        }

        for {
          _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1)

          _ = verify(apiKeyDb).insert(eqTo(expectedEntityWrite))
          _ = verify(apiKeyDataDb).insert(eqTo(expectedDataEntityWrite))
          _ = verify(scopeDb).insertMany(eqTo(expectedScopeEntitiesWrite))
          _ = verify(apiKeyDataScopesDb).insertMany(eqTo(expectedApiKeyDataScopesEntitiesWrite))
        } yield ()
      }

      "return Right containing ApiKeyData" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
        apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
        scopeDb.insertMany(any[List[ScopeEntity.Write]]) returns scopeEntitiesReadWrapped
        apiKeyDataScopesDb.insertMany(
          any[List[ApiKeyDataScopesEntity.Write]]
        ) returns apiKeyDataScopesInsertResultWrapped

        apiKeyRepository.insert(apiKey_1, apiKeyData_1).asserting(_ shouldBe Right(apiKeyData_1))
      }
    }

    "SecureHashGenerator returns failed IO" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt

          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(apiKeyDataDb)
          _ = verifyZeroInteractions(scopeDb)
          _ = verifyZeroInteractions(apiKeyDataScopesDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDb returns Left containing ApiKeyAlreadyExistsError" should {

      "NOT call ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        for {
          _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1)

          _ = verifyZeroInteractions(apiKeyDataDb)
          _ = verifyZeroInteractions(scopeDb)
          _ = verifyZeroInteractions(apiKeyDataScopesDb)
        } yield ()
      }

      "return Left containing ApiKeyAlreadyExistsError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        apiKeyRepository.insert(apiKey_1, apiKeyData_1).asserting(_ shouldBe Left(ApiKeyAlreadyExistsError))
      }
    }

    "ApiKeyDb returns different exception" should {

      "NOT call ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt

          _ = verifyZeroInteractions(apiKeyDataDb)
          _ = verifyZeroInteractions(scopeDb)
          _ = verifyZeroInteractions(apiKeyDataScopesDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyEntity.Read]]

        apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDb returns Right containing ApiKeyEntity.Read" when {

      def fixture[T](test: => IO[T]): IO[T] = IO {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
      }.flatMap(_ => test)

      "ApiKeyDataDb returns Left containing ApiKeyIdAlreadyExistsError" should {

        "NOT call ScopeDb or ApiKeyDataScopesDb" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          for {
            _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1)

            _ = verifyZeroInteractions(scopeDb)
            _ = verifyZeroInteractions(apiKeyDataScopesDb)
          } yield ()
        }

        "return Left containing ApiKeyIdAlreadyExistsError" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository.insert(apiKey_1, apiKeyData_1).asserting(_ shouldBe Left(ApiKeyIdAlreadyExistsError))
        }
      }

      "ApiKeyDataDb returns Left containing PublicKeyIdAlreadyExistsError" should {

        "NOT call ScopeDb or ApiKeyDataScopesDb" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          for {
            _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1)

            _ = verifyZeroInteractions(scopeDb)
            _ = verifyZeroInteractions(apiKeyDataScopesDb)
          } yield ()
        }

        "return Left containing PublicKeyIdAlreadyExistsError" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository.insert(apiKey_1, apiKeyData_1).asserting(_ shouldBe Left(PublicKeyIdAlreadyExistsError))
        }
      }

      "ApiKeyDataDb returns different exception" should {

        "NOT call ScopeDb or ApiKeyDataScopesDb" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns testException
            .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]]

          for {
            _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt

            _ = verifyZeroInteractions(scopeDb)
            _ = verifyZeroInteractions(apiKeyDataScopesDb)
          } yield ()
        }

        "return failed IO containing this exception" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns testException
            .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]]

          apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }

      "ScopeDb returns exception" should {

        def fixture[T](test: => IO[T]): IO[T] = IO {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
        }.flatMap(_ => test)

        "NOT call ApiKeyDataScopesDb" in fixture {
          scopeDb.insertMany(any[List[ScopeEntity.Write]]) returns scopeEntitiesReadWrapped ++ Stream
            .raiseError[doobie.ConnectionIO](testException)

          for {
            _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt

            _ = verifyZeroInteractions(apiKeyDataScopesDb)
          } yield ()
        }

        "return failed IO containing this exception" in fixture {
          scopeDb.insertMany(any[List[ScopeEntity.Write]]) returns scopeEntitiesReadWrapped ++ Stream
            .raiseError[doobie.ConnectionIO](testException)

          apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }

      "ApiKeyDataScopesDb returns exception" should {
        "return failed IO containing this exception" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
          scopeDb.insertMany(any[List[ScopeEntity.Write]]) returns scopeEntitiesReadWrapped
          apiKeyDataScopesDb.insertMany(any[List[ApiKeyDataScopesEntity.Write]]) returns testException
            .raiseError[doobie.ConnectionIO, Int]

          apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }
    }
  }

  "ApiKeyRepository on update" when {

    val apiKeyDataEntityReadWrapped = Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
    val apiKeyDataEntityReadWrappedUpdated = apiKeyDataEntityRead_1
      .copy(
        name = nameUpdated,
        description = descriptionUpdated,
        updatedAt = nowInstant
      )
      .asRight[ApiKeyDataNotFoundError]
      .pure[doobie.ConnectionIO]

    val apiKeyDataScopesStream = Stream
      .emits(
        Seq(
          ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
          ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
        )
      )
      .covary[doobie.ConnectionIO]

    val scopesStream = Stream
      .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
      .covary[doobie.ConnectionIO]

    "everything works correctly" when {

      "ApiKeyData under update has scopes" should {

        "call ApiKeyDataDb, ApiKeyDataScopesDb and ScopeDb, providing correct entities" in {
          apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns apiKeyDataScopesStream
          scopeDb.getByIds(any[List[Long]]) returns scopesStream

          val expectedApiKeyDataEntityUpdate = ApiKeyDataEntity.Update(
            publicKeyId = publicKeyId_1.toString,
            name = nameUpdated,
            description = descriptionUpdated
          )

          for {
            _ <- apiKeyRepository.update(apiKeyDataUpdate_1)

            _ = verify(apiKeyDataDb).update(eqTo(expectedApiKeyDataEntityUpdate))
            _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(eqTo(apiKeyDataEntityRead_1.id))
            _ = verify(scopeDb).getByIds(eqTo(List(101L, 102L)))
          } yield ()
        }

        "return Right containing ApiKeyData" in {
          apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns apiKeyDataScopesStream
          scopeDb.getByIds(any[List[Long]]) returns scopesStream

          val expectedApiKeyData = apiKeyData_1.copy(
            name = nameUpdated,
            description = descriptionUpdated
          )

          apiKeyRepository.update(apiKeyDataUpdate_1).asserting(_ shouldBe Right(expectedApiKeyData))
        }
      }

      "ApiKeyData under update has NO scopes" should {

        "call ApiKeyDataDb, ApiKeyDataScopesDb and ScopeDb, providing correct entities" in {
          apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          val expectedApiKeyDataEntityUpdate = ApiKeyDataEntity.Update(
            publicKeyId = publicKeyId_1.toString,
            name = nameUpdated,
            description = descriptionUpdated
          )

          for {
            _ <- apiKeyRepository.update(apiKeyDataUpdate_1)

            _ = verify(apiKeyDataDb).update(eqTo(expectedApiKeyDataEntityUpdate))
            _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(eqTo(apiKeyDataEntityRead_1.id))
            _ = verify(scopeDb).getByIds(eqTo(List.empty[Long]))
          } yield ()
        }

        "return Right containing ApiKeyData" in {
          apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          val expectedApiKeyData = apiKeyData_1.copy(
            name = nameUpdated,
            description = descriptionUpdated,
            scopes = List.empty
          )

          apiKeyRepository.update(apiKeyDataUpdate_1).asserting(_ shouldBe Right(expectedApiKeyData))
        }
      }
    }

    "ApiKeyDataDb.update returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDataScopesDb or ScopeDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns ApiKeyDataNotFoundError(publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.update(apiKeyDataUpdate_1)

          _ = verifyZeroInteractions(apiKeyDataScopesDb, scopeDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns ApiKeyDataNotFoundError(publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .update(apiKeyDataUpdate_1)
          .asserting(
            _ shouldBe Left(ApiKeyDataNotFoundError(apiKeyDataUpdate_1.publicKeyId))
          )
      }
    }

    "ApiKeyDataDb.update returns exception" should {

      "NOT call ApiKeyDataScopesDb or ScopeDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyDataNotFoundError, ApiKeyDataEntity.Read]]

        for {
          _ <- apiKeyRepository.update(apiKeyDataUpdate_1).attempt

          _ = verifyZeroInteractions(apiKeyDataScopesDb, scopeDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyDataNotFoundError, ApiKeyDataEntity.Read]]

        apiKeyRepository.update(apiKeyDataUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDataScopesDb returns exception" should {

      "NOT call ScopeDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns apiKeyDataScopesStream ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- apiKeyRepository.update(apiKeyDataUpdate_1).attempt

          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns apiKeyDataScopesStream ++ Stream
          .raiseError[doobie.ConnectionIO](testException)

        apiKeyRepository.update(apiKeyDataUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "ScopeDb returns exception" should {
      "return failed IO containing the same exception" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns apiKeyDataEntityReadWrapped
        apiKeyDataDb.update(any[ApiKeyDataEntity.Update]) returns apiKeyDataEntityReadWrappedUpdated
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns apiKeyDataScopesStream
        scopeDb.getByIds(any[List[Long]]) returns scopesStream ++ Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyRepository.update(apiKeyDataUpdate_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

  "ApiKeyRepository on get(:apiKey)" when {

    "should always call SecureHashGenerator" in {
      secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

      for {
        _ <- apiKeyRepository.get(apiKey_1).attempt
        _ = verify(secureHashGenerator).generateHashFor(eqTo(apiKey_1))
      } yield ()
    }

    "SecureHashGenerator returns failed IO" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt
          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(apiKeyDataDb)
          _ = verifyZeroInteractions(scopeDb)
          _ = verifyZeroInteractions(apiKeyDataScopesDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        apiKeyRepository.insert(apiKey_1, apiKeyData_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "SecureHashGenerator returns IO containing HashedApiKey" when {

      "should call ApiKeyDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        apiKeyDb.getByApiKey(any[HashedApiKey]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.get(apiKey_1)
          _ = verify(apiKeyDb).getByApiKey(eqTo(hashedApiKey_1))
        } yield ()
      }

      "ApiKeyDb returns empty Option" should {

        "NOT call ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.getByApiKey(any[HashedApiKey]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.get(apiKey_1)
            _ = verifyZeroInteractions(apiKeyDataDb)
            _ = verifyZeroInteractions(scopeDb)
            _ = verifyZeroInteractions(apiKeyDataScopesDb)
          } yield ()
        }

        "return empty Option" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.getByApiKey(any[HashedApiKey]) returns none[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

          apiKeyRepository.get(apiKey_1).asserting(_ shouldBe None)
        }
      }

      "ApiKeyDb returns ApiKeyEntity" when {

        "should always call ApiKeyDataDb" in {
          secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
          apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
          apiKeyDataDb.getByApiKeyId(any[Long]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.get(apiKey_1)
            _ = verify(apiKeyDataDb).getByApiKeyId(eqTo(apiKeyEntityRead.id))
          } yield ()
        }

        "ApiKeyDataDb returns empty Option" should {

          "NOT call ScopeDb or ApiKeyDataScopesDb" in {
            secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
            apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
            apiKeyDataDb.getByApiKeyId(any[Long]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

            for {
              _ <- apiKeyRepository.get(apiKey_1)
              _ = verifyZeroInteractions(scopeDb)
              _ = verifyZeroInteractions(apiKeyDataScopesDb)
            } yield ()
          }

          "return empty Option" in {
            secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
            apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
            apiKeyDataDb.getByApiKeyId(any[Long]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

            apiKeyRepository.get(apiKey_1).asserting(_ shouldBe None)
          }
        }

        "ApiKeyDataDb returns ApiKeyDataEntity" when {

          "should always call ApiKeyDataScopesDb" in {
            secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
            apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
            apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
            apiKeyDataScopesDb
              .getByApiKeyDataId(any[Long]) returns Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read]()
            scopeDb.getByIds(any[List[Long]]) returns Stream[doobie.ConnectionIO, ScopeEntity.Read]()

            for {
              _ <- apiKeyRepository.get(apiKey_1)
              _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(eqTo(apiKeyDataEntityRead_1.id))
            } yield ()
          }

          "ApiKeyDataScopesDb returns empty Stream" should {

            "call ScopeDb providing empty List" in {
              secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
              apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
              apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
              apiKeyDataScopesDb
                .getByApiKeyDataId(any[Long]) returns Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read]()
              scopeDb.getByIds(any[List[Long]]) returns Stream[doobie.ConnectionIO, ScopeEntity.Read]()

              for {
                _ <- apiKeyRepository.get(apiKey_1)
                _ = verify(scopeDb).getByIds(eqTo(List.empty))
              } yield ()
            }

            "return ApiKeyData with empty scopes field" in {
              secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
              apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
              apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
              apiKeyDataScopesDb
                .getByApiKeyDataId(any[Long]) returns Stream[doobie.ConnectionIO, ApiKeyDataScopesEntity.Read]()
              scopeDb.getByIds(any[List[Long]]) returns Stream[doobie.ConnectionIO, ScopeEntity.Read]()

              apiKeyRepository.get(apiKey_1).asserting { result =>
                result shouldBe defined
                result.get shouldBe apiKeyData_1.copy(scopes = List.empty)
              }
            }
          }

          "ApiKeyDataScopesDb returns non-empty Stream" should {

            "call ScopeDb" in {
              secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
              apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
              apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
              apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream
                .emits(
                  Seq(
                    ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                    ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
                  )
                )
                .covary[doobie.ConnectionIO]
              scopeDb.getByIds(any[List[Long]]) returns Stream
                .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
                .covary[doobie.ConnectionIO]

              for {
                _ <- apiKeyRepository.get(apiKey_1)
                _ = verify(scopeDb).getByIds(eqTo(List(101L, 102L)))
              } yield ()
            }

            "return ApiKeyData with scopes returned from ScopeDb" in {
              secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
              apiKeyDb.getByApiKey(any[HashedApiKey]) returns Option(apiKeyEntityRead).pure[doobie.ConnectionIO]
              apiKeyDataDb.getByApiKeyId(any[Long]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
              apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream
                .emits(
                  Seq(
                    ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                    ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
                  )
                )
                .covary[doobie.ConnectionIO]
              scopeDb.getByIds(any[List[Long]]) returns Stream
                .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
                .covary[doobie.ConnectionIO]

              apiKeyRepository.get(apiKey_1).asserting(_ shouldBe Some(apiKeyData_1))
            }
          }
        }
      }
    }
  }

  "ApiKeyRepository on get(:userId, :publicKeyId)" when {

    "should always call ApiKeyDataDb" in {
      apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.get(userId_1, publicKeyId_1)
        _ = verify(apiKeyDataDb).getBy(eqTo(userId_1), eqTo(publicKeyId_1))
      } yield ()
    }

    "should NOT call ApiKeyDb" in {
      apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.get(userId_1, publicKeyId_1)
        _ = verifyZeroInteractions(apiKeyDb)
      } yield ()
    }

    "ApiKeyDataDb returns empty Option" should {

      "NOT call ScopeDb or ApiKeyDataScopesDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.get(userId_1, publicKeyId_1)
          _ = verifyZeroInteractions(scopeDb, apiKeyDataScopesDb)
        } yield ()
      }

      "return empty Option" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository.get(userId_1, publicKeyId_1).asserting(_ shouldBe Option.empty[ApiKeyDataEntity.Read])
      }
    }

    "ApiKeyDataDb returns ApiKeyDataEntity" when {

      "should always call ApiKeyDataScopesDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
        scopeDb.getByIds(any[List[Long]]) returns Stream.empty

        for {
          _ <- apiKeyRepository.get(userId_1, publicKeyId_1)
          _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(eqTo(apiKeyDataEntityRead_1.id))
        } yield ()
      }

      "ApiKeyDataScopesDb returns empty Stream" should {

        "call ScopeDb providing empty List" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          for {
            _ <- apiKeyRepository.get(userId_1, publicKeyId_1)
            _ = verify(scopeDb).getByIds(eqTo(List.empty))
          } yield ()
        }

        "return ApiKeyData with empty scopes fields" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          apiKeyRepository
            .get(userId_1, publicKeyId_1)
            .asserting(_ shouldBe Some(apiKeyData_1.copy(scopes = List.empty)))
        }
      }

      "ApiKeyDataScopesDb returns non-empty Stream" should {

        "call ScopeDb" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream
            .emits(
              Seq(
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
              )
            )
            .covary[doobie.ConnectionIO]
          scopeDb.getByIds(any[List[Long]]) returns Stream
            .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
            .covary[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.get(userId_1, publicKeyId_1)
            _ = verify(scopeDb).getByIds(eqTo(List(101L, 102L)))
          } yield ()
        }

        "return ApiKeyData with scopes returned from ScopeDb" in {
          apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream
            .emits(
              Seq(
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
              )
            )
            .covary[doobie.ConnectionIO]
          scopeDb.getByIds(any[List[Long]]) returns Stream
            .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
            .covary[doobie.ConnectionIO]

          apiKeyRepository.get(userId_1, publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
        }
      }
    }
  }

  "ApiKeyRepository on getByPublicKeyId" when {

    "should always call ApiKeyDataDb" in {
      apiKeyDataDb.getByPublicKeyId(any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.getByPublicKeyId(publicKeyId_1)
        _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicKeyId_1))
      } yield ()
    }

    "should NOT call ApiKeyDb" in {
      apiKeyDataDb.getByPublicKeyId(any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

      for {
        _ <- apiKeyRepository.getByPublicKeyId(publicKeyId_1)
        _ = verifyZeroInteractions(apiKeyDb)
      } yield ()
    }

    "ApiKeyDataDb returns empty Option" should {

      "NOT call ScopeDb or ApiKeyDataScopesDb" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.getByPublicKeyId(publicKeyId_1)
          _ = verifyZeroInteractions(scopeDb, apiKeyDataScopesDb)
        } yield ()
      }

      "return empty Option" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository.getByPublicKeyId(publicKeyId_1).asserting(_ shouldBe Option.empty[ApiKeyDataEntity.Read])
      }
    }

    "ApiKeyDataDb returns ApiKeyDataEntity" when {

      "should always call ApiKeyDataScopesDb" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
        scopeDb.getByIds(any[List[Long]]) returns Stream.empty

        for {
          _ <- apiKeyRepository.getByPublicKeyId(publicKeyId_1)
          _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(eqTo(apiKeyDataEntityRead_1.id))
        } yield ()
      }

      "ApiKeyDataScopesDb returns empty Stream" should {

        "call ScopeDb providing empty List" in {
          apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          for {
            _ <- apiKeyRepository.getByPublicKeyId(publicKeyId_1)
            _ = verify(scopeDb).getByIds(eqTo(List.empty))
          } yield ()
        }

        "return ApiKeyData with empty scopes fields" in {
          apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          apiKeyRepository
            .getByPublicKeyId(publicKeyId_1)
            .asserting(_ shouldBe Some(apiKeyData_1.copy(scopes = List.empty)))
        }
      }

      "ApiKeyDataScopesDb returns non-empty Stream" should {

        "call ScopeDb" in {
          apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream
            .emits(
              Seq(
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
              )
            )
            .covary[doobie.ConnectionIO]
          scopeDb.getByIds(any[List[Long]]) returns Stream
            .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
            .covary[doobie.ConnectionIO]

          for {
            _ <- apiKeyRepository.getByPublicKeyId(publicKeyId_1)
            _ = verify(scopeDb).getByIds(eqTo(List(101L, 102L)))
          } yield ()
        }

        "return ApiKeyData with scopes returned from ScopeDb" in {
          apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream
            .emits(
              Seq(
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
              )
            )
            .covary[doobie.ConnectionIO]
          scopeDb.getByIds(any[List[Long]]) returns Stream
            .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
            .covary[doobie.ConnectionIO]

          apiKeyRepository.getByPublicKeyId(publicKeyId_1).asserting(_ shouldBe Some(apiKeyData_1))
        }
      }
    }
  }

  "ApiKeyRepository on getAll(:userId)" when {

    "should always call ApiKeyDataDb" in {
      apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAll(userId_1)
        _ = verify(apiKeyDataDb).getByUserId(eqTo(userId_1))
      } yield ()
    }

    "should NOT call ApiKeyDb" in {
      apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

      for {
        _ <- apiKeyRepository.getAll(userId_1)
        _ = verifyZeroInteractions(apiKeyDb)
      } yield ()
    }

    "ApiKeyDataDb returns empty Stream" should {

      "NOT call ScopeDb or ApiKeyDataScopesDb" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

        for {
          _ <- apiKeyRepository.getAll(userId_1)
          _ = verifyZeroInteractions(scopeDb, apiKeyDataScopesDb)
        } yield ()
      }

      "return empty List" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream.empty

        apiKeyRepository.getAll(userId_1).asserting(_ shouldBe List.empty[ApiKeyDataEntity.Read])
      }
    }

    "ApiKeyDataDb returns elements in Stream" when {

      "should always call ApiKeyDataScopesDb" in {
        apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.empty
        scopeDb.getByIds(any[List[Long]]) returns Stream.empty

        for {
          _ <- apiKeyRepository.getAll(userId_1)
          _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(eqTo(apiKeyDataEntityRead_1.id))
        } yield ()
      }

      "ApiKeyDataScopesDb returns empty Stream" should {

        "call ScopeDb providing empty List" in {
          apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
          apiKeyDataScopesDb
            .getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          for {
            _ <- apiKeyRepository.getAll(userId_1)
            _ = verify(scopeDb, times(2)).getByIds(eqTo(List.empty))
          } yield ()
        }

        "return ApiKeyData with empty scopes fields" in {
          apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
          apiKeyDataScopesDb
            .getByApiKeyDataId(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          apiKeyRepository.getAll(userId_1).asserting { result =>
            result.size shouldBe 2
            result should contain theSameElementsAs List(apiKeyData_1, apiKeyData_2).map(_.copy(scopes = List.empty))
          }
        }
      }

      "ApiKeyDataScopesDb returns non-empty Stream" should {

        "call ScopeDb for each found ApiKeyData" in {
          apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns (
            Stream
              .emits(
                Seq(
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
                )
              )
              .covary[doobie.ConnectionIO],
            Stream
              .emits(
                Seq(
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_2.id, 201L, nowInstant, nowInstant),
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_2.id, 202L, nowInstant, nowInstant)
                )
              )
              .covary[doobie.ConnectionIO]
          )

          scopeDb.getByIds(any[List[Long]]) returns (
            Stream
              .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
              .covary[doobie.ConnectionIO],
            Stream
              .emits(Seq(ScopeEntity.Read(201L, scopeRead_2), ScopeEntity.Read(202L, scopeWrite_2)))
              .covary[doobie.ConnectionIO]
          )

          for {
            _ <- apiKeyRepository.getAll(userId_1)
            _ = verify(scopeDb).getByIds(eqTo(List(101L, 102L)))
            _ = verify(scopeDb).getByIds(eqTo(List(201L, 202L)))
          } yield ()
        }

        "return ApiKeyData with scopes returned from ScopeDb" in {
          apiKeyDataDb.getByUserId(any[String]) returns Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns (
            Stream
              .emits(
                Seq(
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 101L, nowInstant, nowInstant),
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, 102L, nowInstant, nowInstant)
                )
              )
              .covary[doobie.ConnectionIO],
            Stream
              .emits(
                Seq(
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_2.id, 201L, nowInstant, nowInstant),
                  ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_2.id, 202L, nowInstant, nowInstant)
                )
              )
              .covary[doobie.ConnectionIO]
          )

          scopeDb.getByIds(any[List[Long]]) returns (
            Stream
              .emits(Seq(ScopeEntity.Read(101L, scopeRead_1), ScopeEntity.Read(102L, scopeWrite_1)))
              .covary[doobie.ConnectionIO],
            Stream
              .emits(Seq(ScopeEntity.Read(201L, scopeRead_2), ScopeEntity.Read(202L, scopeWrite_2)))
              .covary[doobie.ConnectionIO]
          )

          apiKeyRepository.getAll(userId_1).asserting { result =>
            result.size shouldBe 2
            result should contain theSameElementsAs List(apiKeyData_1, apiKeyData_2)
          }
        }
      }
    }
  }

  "ApiKeyRepository on delete(:userId, :publicKeyId)" when {

    val scopeId_1 = 101L
    val scopeId_2 = 102L

    val apiKeyEntityRead_1 = ApiKeyEntity.Read(1L, nowInstant, nowInstant)

    val apiKeyDataScopesEntitiesRead = Seq(
      ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, scopeId_1, nowInstant, nowInstant),
      ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, scopeId_2, nowInstant, nowInstant)
    )

    "everything works correctly" when {

      def initMocks(): Unit = {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns (
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO],
          Stream.empty
        )
        scopeDb.getByIds(any[List[Long]]) returns Stream.empty
        scopeDb.getByIds(eqTo(List(scopeId_1, scopeId_2))) returns Stream
          .emits(Seq(ScopeEntity.Read(scopeId_1, scopeRead_1), ScopeEntity.Read(scopeId_2, scopeWrite_1)))
          .covary[doobie.ConnectionIO]

        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb
          .delete(any[Long]) returns apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]
      }

      "there are scopes for given ApiKeyData" should {

        "call ApiKeyDb, ApiKeyDataDb, ScopeDb and ApiKeyDataScopesDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

            _ = verify(apiKeyDataDb).getBy(eqTo(userId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(apiKeyDataEntityRead_1.id)
            _ = verify(scopeDb).getByIds(eqTo(List(scopeId_1, scopeId_2)))

            _ = verify(apiKeyDataScopesDb).delete(eqTo(apiKeyDataEntityRead_1.id))
            _ = verify(apiKeyDataDb).delete(eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(apiKeyDataEntityRead_1.apiKeyId)

          } yield ()
        }

        "return Right containing deleted ApiKeyData" in {
          initMocks()

          apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
        }
      }

      "there are NO scopes for given ApiKeyData" should {

        "call ApiKeyDb, ApiKeyDataDb, ScopeDb and ApiKeyDataScopesDb" in {
          initMocks()
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          apiKeyDataScopesDb.delete(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          for {
            _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

            _ = verify(apiKeyDataDb).getBy(eqTo(userId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(apiKeyDataEntityRead_1.id)
            _ = verify(scopeDb).getByIds(eqTo(List.empty[Long]))

            _ = verify(apiKeyDataScopesDb, never).delete(any[Long])
            _ = verify(apiKeyDataDb).delete(eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(apiKeyDataEntityRead_1.apiKeyId)

          } yield ()
        }

        "return Right containing deleted ApiKeyData" in {
          initMocks()
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          apiKeyDataScopesDb.delete(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          apiKeyRepository
            .delete(userId_1, publicKeyId_1)
            .asserting(_ shouldBe Right(apiKeyData_1.copy(scopes = List.empty)))
        }
      }
    }

    "ApiKeyDataDb.getBy returns empty Option" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb, never).delete(any[UUID])
          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(scopeDb)
          _ = verifyZeroInteractions(apiKeyDataScopesDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFound" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(userId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(userId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDataScopesDb.delete returns failed Stream" should {

      "NOT call ApiKeyDb, ApiKeyDataDb or ScopeDb anymore" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- apiKeyRepository.delete(userId_1, publicKeyId_1).attempt

          _ = verify(apiKeyDataDb, never).delete(any[UUID])
          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyRepository
          .delete(userId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDb.delete or ScopeDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(userId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(userId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(userId_1, publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(userId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDb.delete returns Left containing ApiKeyNotFoundError" should {

      "NOT call ScopeDb" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[Long]) returns ApiKeyNotFoundError.asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(userId_1, publicKeyId_1)

          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return Left containing ApiKeyNotFoundError" in {
        apiKeyDataDb.getBy(any[String], any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[Long]) returns ApiKeyNotFoundError.asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository.delete(userId_1, publicKeyId_1).asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }
    }
  }

  "ApiKeyRepository on delete(:publicKeyId)" when {

    val scopeId_1 = 101L
    val scopeId_2 = 102L

    val apiKeyEntityRead_1 = ApiKeyEntity.Read(1L, nowInstant, nowInstant)

    val apiKeyDataScopesEntitiesRead = Seq(
      ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, scopeId_1, nowInstant, nowInstant),
      ApiKeyDataScopesEntity.Read(apiKeyDataEntityRead_1.id, scopeId_2, nowInstant, nowInstant)
    )

    "everything works correctly" when {

      def initMocks(): Unit = {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns (
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO],
          Stream.empty
        )
        scopeDb.getByIds(any[List[Long]]) returns Stream.empty
        scopeDb.getByIds(eqTo(List(scopeId_1, scopeId_2))) returns Stream
          .emits(Seq(ScopeEntity.Read(scopeId_1, scopeRead_1), ScopeEntity.Read(scopeId_2, scopeWrite_1)))
          .covary[doobie.ConnectionIO]

        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb
          .delete(any[Long]) returns apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]
      }

      "there are scopes for given ApiKeyData" should {

        "call ApiKeyDb, ApiKeyDataDb, ScopeDb and ApiKeyDataScopesDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.delete(publicKeyId_1)

            _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicKeyId_1))
            _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(apiKeyDataEntityRead_1.id)
            _ = verify(scopeDb).getByIds(eqTo(List(scopeId_1, scopeId_2)))

            _ = verify(apiKeyDataScopesDb).delete(eqTo(apiKeyDataEntityRead_1.id))
            _ = verify(apiKeyDataDb).delete(eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(apiKeyDataEntityRead_1.apiKeyId)

          } yield ()
        }

        "return Right containing deleted ApiKeyData" in {
          initMocks()

          apiKeyRepository.delete(publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
        }
      }

      "there are NO scopes for given ApiKeyData" should {

        "call ApiKeyDb, ApiKeyDataDb, ScopeDb and ApiKeyDataScopesDb" in {
          initMocks()
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          apiKeyDataScopesDb.delete(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          for {
            _ <- apiKeyRepository.delete(publicKeyId_1)

            _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicKeyId_1))
            _ = verify(apiKeyDataScopesDb).getByApiKeyDataId(apiKeyDataEntityRead_1.id)
            _ = verify(scopeDb).getByIds(eqTo(List.empty[Long]))

            _ = verify(apiKeyDataScopesDb, never).delete(any[Long])
            _ = verify(apiKeyDataDb).delete(eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(apiKeyDataEntityRead_1.apiKeyId)

          } yield ()
        }

        "return Right containing deleted ApiKeyData" in {
          initMocks()
          apiKeyDataScopesDb.getByApiKeyDataId(any[Long]) returns Stream.empty
          apiKeyDataScopesDb.delete(any[Long]) returns Stream.empty
          scopeDb.getByIds(any[List[Long]]) returns Stream.empty

          apiKeyRepository
            .delete(publicKeyId_1)
            .asserting(_ shouldBe Right(apiKeyData_1.copy(scopes = List.empty)))
        }
      }
    }

    "ApiKeyDataDb.getByPublicKeyId returns empty Option" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, ScopeDb or ApiKeyDataScopesDb" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicKeyId_1)

          _ = verify(apiKeyDataDb, never).delete(any[UUID])
          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(scopeDb)
          _ = verifyZeroInteractions(apiKeyDataScopesDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFound" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns none[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicKeyId_1)))
      }
    }

    "ApiKeyDataScopesDb.delete returns failed Stream" should {

      "NOT call ApiKeyDb, ApiKeyDataDb or ScopeDb anymore" in {
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        for {
          _ <- apiKeyRepository.delete(publicKeyId_1).attempt

          _ = verify(apiKeyDataDb, never).delete(any[UUID])
          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns Stream.raiseError[doobie.ConnectionIO](testException)

        apiKeyRepository
          .delete(publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDb.delete or ScopeDb" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(userId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicKeyId_1)

          _ = verifyZeroInteractions(apiKeyDb)
          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns ApiKeyDbError
          .apiKeyDataNotFoundError(userId_1, publicKeyId_1)
          .asLeft[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        apiKeyRepository
          .delete(publicKeyId_1)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(userId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDb.delete returns Left containing ApiKeyNotFoundError" should {

      "NOT call ScopeDb" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[Long]) returns ApiKeyNotFoundError.asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicKeyId_1)

          _ = verifyZeroInteractions(scopeDb)
        } yield ()
      }

      "return Left containing ApiKeyNotFoundError" in {
        apiKeyDataDb.getByPublicKeyId(any[UUID]) returns Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeyDataScopesDb
          .getByApiKeyDataId(any[Long]) returns Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataScopesDb.delete(any[Long]) returns
          Stream.emits(apiKeyDataScopesEntitiesRead).covary[doobie.ConnectionIO]
        apiKeyDataDb.delete(any[UUID]) returns apiKeyDataEntityRead_1
          .asRight[ApiKeyDbError]
          .pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[Long]) returns ApiKeyNotFoundError.asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository.delete(publicKeyId_1).asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }
    }
  }

  "ApiKeyRepository on getAllUserIds" should {

    "call ApiKeyDataDb" in {
      apiKeyDataDb.getAllUserIds returns Stream.empty

      for {
        _ <- apiKeyRepository.getAllUserIds
        _ <- IO(verify(apiKeyDataDb).getAllUserIds)
      } yield ()
    }

    "return userIds obtained from getAllUserIds" when {

      "getAllUserIds returns empty Stream" in {
        apiKeyDataDb.getAllUserIds returns Stream.empty

        apiKeyRepository.getAllUserIds.asserting(_ shouldBe List.empty[String])
      }

      "getAllUserIds returns elements in Stream" in {
        apiKeyDataDb.getAllUserIds returns Stream(userId_1, userId_2)

        apiKeyRepository.getAllUserIds.asserting { result =>
          result.size shouldBe 2
          result shouldBe List(userId_1, userId_2)
        }
      }
    }
  }
}
