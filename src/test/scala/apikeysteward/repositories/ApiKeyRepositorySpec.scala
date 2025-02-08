package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.ApiKeyTemplatesTestData._
import apikeysteward.base.testdata.ApiKeysPermissionsTestData.apiKeysPermissionsEntityWrite_1_1
import apikeysteward.base.testdata.ApiKeysTestData._
import apikeysteward.base.testdata.PermissionsTestData._
import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenantDbId_1, tenantEntityRead_1}
import apikeysteward.base.testdata.UsersTestData.{publicUserId_1, userDbId_1, userEntityRead_1, userEntityRead_2}
import apikeysteward.model.ApiKeyData.ApiKeyId
import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeyDbError.ApiKeyInsertionError._
import apikeysteward.model.errors.ApiKeyDbError._
import apikeysteward.model.errors.ApiKeysPermissionsDbError.ApiKeysPermissionsInsertionError.ApiKeysPermissionsInsertionErrorImpl
import apikeysteward.model.errors.{ApiKeyDbError, ApiKeysPermissionsDbError}
import apikeysteward.model.{ApiKey, ApiKeyData, HashedApiKey}
import apikeysteward.repositories.db._
import apikeysteward.repositories.db.entity._
import apikeysteward.services.UuidGenerator
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import doobie.implicits._
import fs2.Stream
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito.verify
import org.mockito.MockitoSugar._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}

import java.sql.SQLException
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
  private val secureHashGenerator = mock[SecureHashGenerator]
  private val apiKeyDb = mock[ApiKeyDb]
  private val tenantDb = mock[TenantDb]
  private val apiKeyDataDb = mock[ApiKeyDataDb]
  private val permissionDb = mock[PermissionDb]
  private val userDb = mock[UserDb]
  private val apiKeyTemplateDb = mock[ApiKeyTemplateDb]
  private val apiKeysPermissionsDb = mock[ApiKeysPermissionsDb]

  private val apiKeyRepository =
    new ApiKeyRepository(
      uuidGenerator,
      secureHashGenerator,
      tenantDb,
      apiKeyDb,
      apiKeyDataDb,
      permissionDb,
      userDb,
      apiKeyTemplateDb,
      apiKeysPermissionsDb
    )(noopTransactor)

  private val tenantEntityReadWrapped: doobie.ConnectionIO[Option[TenantEntity.Read]] =
    Option(tenantEntityRead_1).pure[doobie.ConnectionIO]

  private val userEntityReadWrapped_1: doobie.ConnectionIO[Option[UserEntity.Read]] =
    Option(userEntityRead_1).pure[doobie.ConnectionIO]

  private val userEntityReadWrapped_2: doobie.ConnectionIO[Option[UserEntity.Read]] =
    Option(userEntityRead_2).pure[doobie.ConnectionIO]

  private val templateEntityReadWrapped_1: doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Option(apiKeyTemplateEntityRead_1).pure[doobie.ConnectionIO]

  private val templateEntityReadWrapped_2: doobie.ConnectionIO[Option[ApiKeyTemplateEntity.Read]] =
    Option(apiKeyTemplateEntityRead_2).pure[doobie.ConnectionIO]

  private val permissionEntityReadWrapped_1: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_1).pure[doobie.ConnectionIO]

  private val permissionEntityReadWrapped_2: doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Option(permissionEntityRead_2).pure[doobie.ConnectionIO]

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
  private val testSqlException = new SQLException("Test SQL Exception")

  override def beforeEach(): Unit = {
    reset(
      uuidGenerator,
      secureHashGenerator,
      tenantDb,
      apiKeyDb,
      apiKeyDataDb,
      permissionDb,
      userDb,
      apiKeyTemplateDb,
      apiKeysPermissionsDb
    )

    userDb.getByDbId(any[TenantId], any[UUID]) returns userEntityReadWrapped_1
    apiKeyTemplateDb.getByDbId(any[TenantId], any[UUID]) returns templateEntityReadWrapped_1
    permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns Stream(permissionEntityRead_1)
  }

  "ApiKeyRepository on insert" when {

    val apiKeyEntityReadWrapped = apiKeyEntityRead_1.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]
    val apiKeyDataEntityReadWrapped = apiKeyDataEntityRead_1.asRight[ApiKeyInsertionError].pure[doobie.ConnectionIO]

    val apiKeyAlreadyExistsErrorWrapped =
      ApiKeyAlreadyExistsError.asInstanceOf[ApiKeyInsertionError].asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]

    val apiKeysPermissionsEntityRead_1_1: ApiKeysPermissionsEntity.Read =
      ApiKeysPermissionsEntity
        .Read(tenantId = tenantDbId_1, apiKeyDataId = apiKeyDataDbId_1, permissionId = permissionDbId_1)

    def initMocks(): Unit = {
      secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
      uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
      tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
      userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
      apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
      permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns permissionEntityReadWrapped_1
      apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
      apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
      apiKeysPermissionsDb.insertMany(any[List[ApiKeysPermissionsEntity.Write]]) returns
        List(apiKeysPermissionsEntityRead_1_1).asRight[ApiKeysPermissionsDbError].pure[doobie.ConnectionIO]
    }

    "everything works correctly" should {

      "call SecureHashGenerator, UuidGenerator, TenantDb, UserDb, ApiKeyTemplateDb, PermissionDb, ApiKeyDb and ApiKeyDataDb, providing correct entities" in {
        initMocks()

        val expectedEntityWrite =
          ApiKeyEntity.Write(id = apiKeyDbId_1, tenantId = tenantDbId_1, apiKey = hashedApiKey_1.value)

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

          _ = verify(secureHashGenerator).generateHashFor(eqTo(apiKey_1))
          _ = verify(uuidGenerator, times(2)).generateUuid
          _ = verify(tenantDb).getByPublicTenantId(eqTo(publicTenantId_1))
          _ = verify(userDb).getByPublicUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))
          _ = verify(apiKeyTemplateDb).getByPublicTemplateId(eqTo(publicTenantId_1), eqTo(publicTemplateId_1))
          _ = verify(permissionDb).getByPublicPermissionId(eqTo(publicTenantId_1), eqTo(publicPermissionId_1))

          _ = verify(apiKeyDb).insert(eqTo(expectedEntityWrite))
          _ = verify(apiKeyDataDb).insert(eqTo(apiKeyDataEntityWrite_1))
          _ = verify(apiKeysPermissionsDb).insertMany(eqTo(List(apiKeysPermissionsEntityWrite_1_1)))
        } yield ()
      }

      "return Right containing ApiKeyData" in {
        initMocks()

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .asserting(_ shouldBe Right(apiKeyData_1))
      }
    }

    "SecureHashGenerator returns failed IO" should {

      "NOT call UuidGenerator, TenantDb, UserDb, ApiKeyTemplateDb, ApiKeyDb, ApiKeyDataDb or PermissionDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(
            uuidGenerator,
            tenantDb,
            userDb,
            apiKeyTemplateDb,
            permissionDb,
            apiKeyDb,
            apiKeyDataDb
          )
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.raiseError(testException)

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UuidGenerator returns failed IO" should {

      "NOT call TenantDb, UserDb, ApiKeyTemplateDb, ApiKeyDb, ApiKeyDataDb or PermissionDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(tenantDb, userDb, apiKeyTemplateDb, permissionDb, apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing the same exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns IO.raiseError(testException)

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "TenantDb returns empty Option" should {

      "NOT call UserDb, ApiKeyTemplateDb, ApiKeyDb, ApiKeyDataDb or PermissionDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

          _ = verifyZeroInteractions(userDb, apiKeyTemplateDb, permissionDb, apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return Left containing ReferencedTenantDoesNotExistError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns none[TenantEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ReferencedTenantDoesNotExistError(publicTenantId_1)))
      }
    }

    "TenantDb returns exception" should {

      "NOT call UserDb, ApiKeyTemplateDb, ApiKeyDb, ApiKeyDataDb or PermissionDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(userDb, apiKeyTemplateDb, permissionDb, apiKeyDb, apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns testException
          .raiseError[doobie.ConnectionIO, Option[TenantEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "UserDb.getByPublicUserId returns empty Option" should {

      "NOT call ApiKeyTemplateDb, ApiKeyDb, ApiKeyDataDb, PermissionDb or UserDb.getByDbId" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb, apiKeyDb, apiKeyDataDb)
          _ = verify(userDb, never).getByDbId(any[TenantId], any[UUID])
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError(publicUserId_1)))
      }
    }

    "UserDb.getByPublicUserId returns exception" should {

      "NOT call ApiKeyTemplateDb, ApiKeyDb, ApiKeyDataDb, PermissionDb or UserDb.getByDbId" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyTemplateDb, permissionDb, apiKeyDb, apiKeyDataDb)
          _ = verify(userDb, never).getByDbId(any[TenantId], any[UUID])
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyTemplateDb.getByPublicTemplateId returns empty Option" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, PermissionDb, ApiKeyTemplateDb.getByDbId or UserDb.getByDbId" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns
          none[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

          _ = verifyZeroInteractions(permissionDb, apiKeyDb, apiKeyDataDb)
          _ = verify(userDb, never).getByDbId(any[TenantId], any[UUID])
          _ = verify(apiKeyTemplateDb, never).getByDbId(any[TenantId], any[ApiKeyTemplateId])
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns
          none[ApiKeyTemplateEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ReferencedApiKeyTemplateDoesNotExistError(publicTemplateId_1)))
      }
    }

    "ApiKeyTemplateDb.getByPublicTemplateId returns exception" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, PermissionDb, ApiKeyTemplateDb.getByDbId or UserDb.getByDbId" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns
          testException.raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(permissionDb, apiKeyDb, apiKeyDataDb)
          _ = verify(userDb, never).getByDbId(any[TenantId], any[UUID])
          _ = verify(apiKeyTemplateDb, never).getByDbId(any[TenantId], any[ApiKeyTemplateId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns
          testException.raiseError[doobie.ConnectionIO, Option[ApiKeyTemplateEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb.getByPublicPermissionId returns empty Option" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, PermissionDb, ApiKeyTemplateDb.getByDbId or UserDb.getByDbId" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns
          none[PermissionEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

          _ = verifyZeroInteractions(apiKeyDb, apiKeyDataDb)
          _ = verify(userDb, never).getByDbId(any[TenantId], any[UUID])
          _ = verify(apiKeyTemplateDb, never).getByDbId(any[TenantId], any[ApiKeyTemplateId])
          _ = verify(permissionDb, never).getAllForApiKey(any[TenantId], any[ApiKeyId])
        } yield ()
      }

      "return Left containing ReferencedApiKeyTemplateDoesNotExistError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns
          none[PermissionEntity.Read].pure[doobie.ConnectionIO]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ReferencedPermissionDoesNotExistError(publicPermissionId_1)))
      }
    }

    "PermissionDb.getByPublicPermissionId returns exception" should {

      "NOT call ApiKeyDb, ApiKeyDataDb, PermissionDb, ApiKeyTemplateDb.getByDbId or UserDb.getByDbId" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyDb, apiKeyDataDb)
          _ = verify(userDb, never).getByDbId(any[TenantId], any[UUID])
          _ = verify(apiKeyTemplateDb, never).getByDbId(any[TenantId], any[ApiKeyTemplateId])
          _ = verify(permissionDb, never).getAllForApiKey(any[TenantId], any[ApiKeyId])
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns
          testException.raiseError[doobie.ConnectionIO, Option[PermissionEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDb returns Left containing ApiKeyAlreadyExistsError" should {

      "NOT call ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns permissionEntityReadWrapped_1
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

          _ = verifyZeroInteractions(apiKeyDataDb)
        } yield ()
      }

      "return Left containing ApiKeyAlreadyExistsError" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns permissionEntityReadWrapped_1
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyAlreadyExistsErrorWrapped

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .asserting(_ shouldBe Left(ApiKeyAlreadyExistsError))
      }
    }

    "ApiKeyDb returns different exception" should {

      "NOT call ApiKeyDataDb" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns permissionEntityReadWrapped_1
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyEntity.Read]]

        for {
          _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

          _ = verifyZeroInteractions(apiKeyDataDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns permissionEntityReadWrapped_1
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns testException
          .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyEntity.Read]]

        apiKeyRepository
          .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDb returns Right containing ApiKeyEntity.Read" when {

      def fixture[T](test: => IO[T]): IO[T] = IO {
        secureHashGenerator.generateHashFor(any[ApiKey]) returns IO.pure(hashedApiKey_1)
        uuidGenerator.generateUuid returns (IO.pure(apiKeyDbId_1), IO.pure(apiKeyDataDbId_1))
        tenantDb.getByPublicTenantId(any[TenantId]) returns tenantEntityReadWrapped
        userDb.getByPublicUserId(any[TenantId], any[UserId]) returns userEntityReadWrapped_1
        apiKeyTemplateDb.getByPublicTemplateId(any[TenantId], any[ApiKeyTemplateId]) returns templateEntityReadWrapped_1
        permissionDb.getByPublicPermissionId(any[TenantId], any[PermissionId]) returns permissionEntityReadWrapped_1
        apiKeyDb.insert(any[ApiKeyEntity.Write]) returns apiKeyEntityReadWrapped
      }.flatMap(_ => test)

      "ApiKeyDataDb returns Left containing ApiKeyIdAlreadyExistsError" should {

        "NOT call ApiKeysPermissionsDb" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          for {
            _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

            _ = verifyZeroInteractions(apiKeysPermissionsDb)
          } yield ()
        }

        "return Left containing ApiKeyIdAlreadyExistsError" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
            .asserting(_ shouldBe Left(ApiKeyIdAlreadyExistsError))
        }
      }

      "ApiKeyDataDb returns Left containing PublicKeyIdAlreadyExistsError" should {

        "NOT call ApiKeysPermissionsDb" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          for {
            _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)

            _ = verifyZeroInteractions(apiKeysPermissionsDb)
          } yield ()
        }

        "return Left containing PublicKeyIdAlreadyExistsError" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns publicKeyIdAlreadyExistsErrorWrapped

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
            .asserting(_ shouldBe Left(PublicKeyIdAlreadyExistsError))
        }
      }

      "ApiKeyDataDb returns different exception" should {

        "NOT call ApiKeysPermissionsDb" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns testException
            .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]]

          for {
            _ <- apiKeyRepository.insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1).attempt

            _ = verifyZeroInteractions(apiKeysPermissionsDb)
          } yield ()
        }

        "return failed IO containing this exception" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns testException
            .raiseError[doobie.ConnectionIO, Either[ApiKeyInsertionError, ApiKeyDataEntity.Read]]

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
            .attempt
            .asserting(_ shouldBe Left(testException))
        }
      }

      "ApiKeysPermissionsDb returns Left containing ApiKeysPermissionsDbError" should {
        "return failed IO containing ApiKeyPermissionAssociationCannotBeCreated" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
          apiKeysPermissionsDb.insertMany(any[List[ApiKeysPermissionsEntity.Write]]) returns
            ApiKeysPermissionsInsertionErrorImpl(testSqlException)
              .asInstanceOf[ApiKeysPermissionsDbError]
              .asLeft[List[ApiKeysPermissionsEntity.Read]]
              .pure[doobie.ConnectionIO]

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
            .asserting(
              _ shouldBe Left(
                ApiKeyPermissionAssociationCannotBeCreated(ApiKeysPermissionsInsertionErrorImpl(testSqlException))
              )
            )
        }
      }

      "ApiKeysPermissionsDb returns exception" should {
        "return failed IO containing this exception" in fixture {
          apiKeyDataDb.insert(any[ApiKeyDataEntity.Write]) returns apiKeyDataEntityReadWrapped
          apiKeysPermissionsDb.insertMany(any[List[ApiKeysPermissionsEntity.Write]]) returns testException
            .raiseError[doobie.ConnectionIO, Either[ApiKeysPermissionsDbError, List[ApiKeysPermissionsEntity.Read]]]

          apiKeyRepository
            .insert(publicTenantId_1, apiKey_1, apiKeyData_1, publicTemplateId_1)
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
        userDb.getByDbId(any[TenantId], any[UUID]) returns (
          Option(userEntityRead_1).pure[doobie.ConnectionIO],
          Option(userEntityRead_2).pure[doobie.ConnectionIO]
        )
        apiKeyTemplateDb.getByDbId(any[TenantId], any[UUID]) returns (
          Option(apiKeyTemplateEntityRead_1).pure[doobie.ConnectionIO],
          Option(apiKeyTemplateEntityRead_2).pure[doobie.ConnectionIO]
        )
        permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
          Stream(permissionEntityRead_1),
          Stream(permissionEntityRead_2)
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

      "ApiKeysPermissionsDb.deleteAllForApiKey returns value greater than zero" should {
        def initMocks(): Unit = {
          apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
            .pure[doobie.ConnectionIO]
          apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
          apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
            .asRight[ApiKeyDbError]
            .pure[doobie.ConnectionIO]
          apiKeyDb.delete(any[TenantId], any[UUID]) returns apiKeyEntityRead_1
            .asRight[ApiKeyNotFoundError.type]
            .pure[doobie.ConnectionIO]
        }

        "call ApiKeysPermissionsDb, ApiKeyDb and ApiKeyDataDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1)

            _ = verify(apiKeyDataDb).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
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

      "ApiKeysPermissionsDb.deleteAllForApiKey returns zero" should {

        def initMocks(): Unit = {
          apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
            .pure[doobie.ConnectionIO]
          apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 0.pure[doobie.ConnectionIO]
          apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
            .asRight[ApiKeyDbError]
            .pure[doobie.ConnectionIO]
          apiKeyDb.delete(any[TenantId], any[UUID]) returns apiKeyEntityRead_1
            .asRight[ApiKeyNotFoundError.type]
            .pure[doobie.ConnectionIO]
        }

        "call ApiKeysPermissionsDb, ApiKeyDb and ApiKeyDataDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1)

            _ = verify(apiKeyDataDb).getBy(eqTo(publicTenantId_1), eqTo(publicUserId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
          } yield ()
        }

        "return Right containing deleted ApiKeyData without any Permission" in {
          initMocks()
          permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns Stream.empty

          apiKeyRepository
            .delete(publicTenantId_1, publicUserId_1, publicKeyId_1)
            .asserting(_ shouldBe Right(apiKeyData_1.copy(permissions = List.empty)))
        }
      }
    }

    "ApiKeyDataDb.getBy returns empty Option" should {

      "NOT call ApiKeysPermissionsDb, ApiKeyDb or ApiKeyDataDb.delete" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb, never).delete(any[TenantId], any[ApiKeyId])
          _ = verifyZeroInteractions(apiKeysPermissionsDb, apiKeyDb)
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

    "ApiKeysPermissionsDb.deleteAllForApiKey returns exception" should {

      "NOT call ApiKeyDataDb.delete or ApiKeyDb" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns
          Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns
          testException.raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicUserId_1, publicKeyId_1).attempt

          _ = verifyZeroInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing this exception" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns
          Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns
          testException.raiseError[doobie.ConnectionIO, Int]

        apiKeyRepository
          .delete(publicTenantId_1, publicUserId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDb" in {
        apiKeyDataDb.getBy(any[TenantId], any[UserId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
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
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
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
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
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

      "ApiKeysPermissionsDb.deleteAllForApiKey returns value greater than zero" should {

        def initMocks(): Unit = {
          apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
            .pure[doobie.ConnectionIO]
          apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
          apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
            .asRight[ApiKeyDbError]
            .pure[doobie.ConnectionIO]
          apiKeyDb.delete(any[TenantId], any[UUID]) returns apiKeyEntityRead_1
            .asRight[ApiKeyNotFoundError.type]
            .pure[doobie.ConnectionIO]
        }

        "call ApiKeysPermissionsDb, ApiKeyDb and ApiKeyDataDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1)

            _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
          } yield ()
        }

        "return Right containing deleted ApiKeyData with Permissions" in {
          initMocks()

          apiKeyRepository.delete(publicTenantId_1, publicKeyId_1).asserting(_ shouldBe Right(apiKeyData_1))
        }
      }

      "ApiKeysPermissionsDb.deleteAllForApiKey returns zero" should {

        def initMocks(): Unit = {
          apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
            .pure[doobie.ConnectionIO]
          apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 0.pure[doobie.ConnectionIO]
          apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns apiKeyDataEntityRead_1
            .asRight[ApiKeyDbError]
            .pure[doobie.ConnectionIO]
          apiKeyDb.delete(any[TenantId], any[UUID]) returns apiKeyEntityRead_1
            .asRight[ApiKeyNotFoundError.type]
            .pure[doobie.ConnectionIO]
        }

        "call ApiKeysPermissionsDb, ApiKeyDb and ApiKeyDataDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1)

            _ = verify(apiKeyDataDb).getByPublicKeyId(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
          } yield ()
        }

        "return Right containing deleted ApiKeyData without any Permission" in {
          initMocks()
          permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns Stream.empty

          apiKeyRepository
            .delete(publicTenantId_1, publicKeyId_1)
            .asserting(_ shouldBe Right(apiKeyData_1.copy(permissions = List.empty)))
        }
      }
    }

    "ApiKeyDataDb.getByPublicKeyId returns empty Option" should {

      "NOT call ApiKeysPermissionsDb, ApiKeyDb or ApiKeyDataDb.delete" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns none[ApiKeyDataEntity.Read]
          .pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1)

          _ = verify(apiKeyDataDb, never).delete(any[TenantId], any[ApiKeyId])
          _ = verifyZeroInteractions(apiKeysPermissionsDb, apiKeyDb)
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

    "ApiKeysPermissionsDb.deleteAllForApiKey returns exception" should {

      "NOT call ApiKeyDataDb.delete or ApiKeyDb" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns
          Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns
          testException.raiseError[doobie.ConnectionIO, Int]

        for {
          _ <- apiKeyRepository.delete(publicTenantId_1, publicKeyId_1).attempt

          _ = verifyZeroInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing this exception" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns
          Option(apiKeyDataEntityRead_1).pure[doobie.ConnectionIO]
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns
          testException.raiseError[doobie.ConnectionIO, Int]

        apiKeyRepository
          .delete(publicTenantId_1, publicKeyId_1)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError" should {

      "NOT call ApiKeyDb" in {
        apiKeyDataDb.getByPublicKeyId(any[TenantId], any[ApiKeyId]) returns Option(apiKeyDataEntityRead_1)
          .pure[doobie.ConnectionIO]
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
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
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
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
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns 1.pure[doobie.ConnectionIO]
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

  "ApiKeyRepository on deleteAllForUserOp(:userId)" when {

    "everything works correctly" when {

      "ApiKeysPermissionsDb.deleteAllForApiKey returns value greater than zero" should {

        def initMocks(): Unit = {
          apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
            Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)

          userDb.getByDbId(any[TenantId], any[UUID]) returns (userEntityReadWrapped_1, userEntityReadWrapped_2)
          apiKeyTemplateDb.getByDbId(any[TenantId], any[UUID]) returns
            (templateEntityReadWrapped_1, templateEntityReadWrapped_2)
          permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns
            (Stream(permissionEntityRead_1), Stream(permissionEntityRead_2))

          apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
            1.pure[doobie.ConnectionIO],
            1.pure[doobie.ConnectionIO]
          )
          apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns (
            apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO],
            apiKeyDataEntityRead_2.asRight[ApiKeyDbError].pure[doobie.ConnectionIO]
          )
          apiKeyDb.delete(any[TenantId], any[UUID]) returns (
            apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO],
            apiKeyEntityRead_2.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]
          )
        }

        "call ApiKeysPermissionsDb, ApiKeyDb and ApiKeyDataDb for each deleted API key" in {
          initMocks()

          for {
            _ <- apiKeyRepository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(noopTransactor)

            _ = verify(apiKeyDataDb).getByUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))

            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_2))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_2))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_2.apiKeyId))
          } yield ()
        }

        "return Right containing all deleted ApiKeyData with Permissions" in {
          initMocks()

          apiKeyRepository
            .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
            .value
            .transact(noopTransactor)
            .asserting(_ shouldBe Right(List(apiKeyData_1, apiKeyData_2)))
        }
      }

      "ApiKeysPermissionsDb.deleteAllForApiKey returns zero" should {

        def initMocks(): Unit = {
          apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
            Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)

          userDb.getByDbId(any[TenantId], any[UUID]) returns (userEntityReadWrapped_1, userEntityReadWrapped_2)
          apiKeyTemplateDb.getByDbId(any[TenantId], any[UUID]) returns
            (templateEntityReadWrapped_1, templateEntityReadWrapped_2)
          permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns
            (Stream.empty, Stream(permissionEntityRead_2))

          apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
            0.pure[doobie.ConnectionIO],
            1.pure[doobie.ConnectionIO]
          )
          apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns (
            apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO],
            apiKeyDataEntityRead_2.asRight[ApiKeyDbError].pure[doobie.ConnectionIO]
          )
          apiKeyDb.delete(any[TenantId], any[UUID]) returns (
            apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO],
            apiKeyEntityRead_2.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]
          )
        }

        "call ApiKeysPermissionsDb, ApiKeyDb and ApiKeyDataDb" in {
          initMocks()

          for {
            _ <- apiKeyRepository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(noopTransactor)

            _ = verify(apiKeyDataDb).getByUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))

            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeysPermissionsDb).deleteAllForApiKey(eqTo(publicTenantId_1), eqTo(publicKeyId_2))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
            _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_2))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
            _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_2.apiKeyId))
          } yield ()
        }

        "return Right containing all deleted ApiKeyData" in {
          initMocks()

          apiKeyRepository
            .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
            .value
            .transact(noopTransactor)
            .asserting(_ shouldBe Right(List(apiKeyData_1.copy(permissions = List.empty), apiKeyData_2)))
        }
      }
    }

    "ApiKeyDataDb.getByUserId returns empty Stream" should {

      "NOT call ApiKeysPermissionsDb, ApiKeyDb or ApiKeyDataDb.delete" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns Stream.empty

        for {
          _ <- apiKeyRepository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(noopTransactor)

          _ = verify(apiKeyDataDb, never).delete(any[TenantId], any[ApiKeyId])
          _ = verifyZeroInteractions(apiKeysPermissionsDb, apiKeyDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFound" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns Stream.empty

        apiKeyRepository
          .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
          .value
          .transact(noopTransactor)
          .asserting(_ shouldBe Right(List.empty))
      }
    }

    "ApiKeysPermissionsDb.deleteAllForApiKey returns exception for subsequent call" should {

      "call ApiKeyDataDb.delete and ApiKeyDb only for the successful call" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
          Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
          1.pure[doobie.ConnectionIO],
          testException.raiseError[doobie.ConnectionIO, Int]
        )
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns
          apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[TenantId], any[UUID]) returns
          apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository
            .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
            .value
            .transact(noopTransactor)
            .attempt

          _ = verify(apiKeyDataDb).getByUserId(eqTo(publicTenantId_1), eqTo(publicUserId_1))

          _ = verify(apiKeyDataDb).delete(eqTo(publicTenantId_1), eqTo(publicKeyId_1))
          _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
          _ = verifyNoMoreInteractions(apiKeyDataDb, apiKeyDb)
        } yield ()
      }

      "return Left containing this exception" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
          Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
          testException.raiseError[doobie.ConnectionIO, Int],
          1.pure[doobie.ConnectionIO]
        )
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns
          apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO]
        apiKeyDb.delete(any[TenantId], any[UUID]) returns
          apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]

        apiKeyRepository
          .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
          .value
          .transact(noopTransactor)
          .attempt
          .asserting(_ shouldBe Left(testException))
      }
    }

    "ApiKeyDataDb.delete returns Left containing ApiKeyDataNotFoundError for subsequent call" should {

      val error: ApiKeyDbError = ApiKeyDbError.apiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)

      "call ApiKeyDb only for the successful call" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
          Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
          1.pure[doobie.ConnectionIO],
          1.pure[doobie.ConnectionIO]
        )
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns (
          apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO],
          error.asLeft[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]
        )
        apiKeyDb.delete(any[TenantId], any[UUID]) returns
          apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]

        for {
          _ <- apiKeyRepository.deleteAllForUserOp(publicTenantId_1, publicUserId_1).value.transact(noopTransactor)

          _ = verify(apiKeyDb).delete(eqTo(publicTenantId_1), eqTo(apiKeyDataEntityRead_1.apiKeyId))
          _ = verifyNoMoreInteractions(apiKeyDb)
        } yield ()
      }

      "return Left containing ApiKeyDataNotFoundError" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
          Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
          1.pure[doobie.ConnectionIO],
          1.pure[doobie.ConnectionIO]
        )
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns (
          apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO],
          error.asLeft[ApiKeyDataEntity.Read].pure[doobie.ConnectionIO]
        )
        apiKeyDb.delete(any[TenantId], any[UUID]) returns
          apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO]

        apiKeyRepository
          .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
          .value
          .transact(noopTransactor)
          .asserting(_ shouldBe Left(ApiKeyDataNotFoundError(publicUserId_1, publicKeyId_1)))
      }
    }

    "ApiKeyDb.delete returns Left containing ApiKeyNotFoundError for subsequent call" should {
      "return Left containing ApiKeyNotFoundError" in {
        apiKeyDataDb.getByUserId(any[TenantId], any[UserId]) returns
          Stream(apiKeyDataEntityRead_1, apiKeyDataEntityRead_2)
        apiKeysPermissionsDb.deleteAllForApiKey(any[TenantId], any[ApiKeyId]) returns (
          1.pure[doobie.ConnectionIO],
          1.pure[doobie.ConnectionIO]
        )
        apiKeyDataDb.delete(any[TenantId], any[ApiKeyId]) returns (
          apiKeyDataEntityRead_1.asRight[ApiKeyDbError].pure[doobie.ConnectionIO],
          apiKeyDataEntityRead_2.asRight[ApiKeyDbError].pure[doobie.ConnectionIO]
        )
        apiKeyDb.delete(any[TenantId], any[UUID]) returns (
          apiKeyEntityRead_1.asRight[ApiKeyNotFoundError.type].pure[doobie.ConnectionIO],
          ApiKeyNotFoundError.asLeft[ApiKeyEntity.Read].pure[doobie.ConnectionIO]
        )

        apiKeyRepository
          .deleteAllForUserOp(publicTenantId_1, publicUserId_1)
          .value
          .transact(noopTransactor)
          .asserting(_ shouldBe Left(ApiKeyNotFoundError))
      }
    }
  }

  "ApiKeyRepository on constructApiKeyData" when {

    import doobie.implicits._

    def methodUnderTest(
        publicTenantId: TenantId,
        apiKeyDataEntity: ApiKeyDataEntity.Read
    ): IO[Either[ApiKeyDbError, ApiKeyData]] =
      apiKeyRepository
        .constructApiKeyData(publicTenantId, apiKeyDataEntity)
        .value
        .transact(noopTransactor)

    "everything works correctly" should {

      "call UserDb and PermissionDb" in {
        for {
          _ <- methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1)

          _ = verify(userDb).getByDbId(any[TenantId], any[UUID])
          _ = verify(permissionDb).getAllForApiKey(any[TenantId], any[ApiKeyId])
        } yield ()
      }

      "return Right containing ApiKeyData" in {
        methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1)
          .asserting(_ shouldBe Right(apiKeyData_1))
      }
    }

    "UserDb.getByDbId returns empty Option" should {

      "NOT call PermissionDb" in {
        userDb.getByDbId(any[TenantId], any[UUID]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        for {
          _ <- methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1)

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return Left containing ReferencedUserDoesNotExistError" in {
        userDb.getByDbId(any[TenantId], any[UUID]) returns none[UserEntity.Read].pure[doobie.ConnectionIO]

        methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1)
          .asserting(_ shouldBe Left(ReferencedUserDoesNotExistError.fromDbId(userDbId_1)))
      }
    }

    "UserDb.getByDbId returns exception" should {

      "NOT call PermissionDb" in {
        userDb.getByDbId(any[TenantId], any[UUID]) returns
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        for {
          _ <- methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1).attempt

          _ = verifyZeroInteractions(permissionDb)
        } yield ()
      }

      "return failed IO containing this exception" in {
        userDb.getByDbId(any[TenantId], any[UUID]) returns
          testException.raiseError[doobie.ConnectionIO, Option[UserEntity.Read]]

        methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }

    "PermissionDb.getAllForApiKey returns exception" should {
      "return failed IO containing this exception" in {
        permissionDb.getAllForApiKey(any[TenantId], any[ApiKeyId]) returns
          Stream(permissionEntityRead_1, permissionEntityRead_2) ++
          Stream.raiseError[doobie.ConnectionIO](testException)

        methodUnderTest(publicTenantId_1, apiKeyDataEntityRead_1).attempt.asserting(_ shouldBe Left(testException))
      }
    }
  }

}
