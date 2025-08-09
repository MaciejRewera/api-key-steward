package apikeysteward.repositories

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.Auth0LoginTestData._
import apikeysteward.connectors.Auth0LoginConnector
import apikeysteward.connectors.Auth0LoginConnector.Auth0LoginResponse
import apikeysteward.model.errors.Auth0Error.Auth0LoginError
import apikeysteward.model.errors.Auth0Error.Auth0LoginError.{Auth0LoginUpsertError, Auth0LoginUpstreamErrorResponse}
import apikeysteward.repositories.db.Auth0LoginDb
import apikeysteward.repositories.db.entity.Auth0LoginEntity
import apikeysteward.routes.auth.model.{JwtClaimCustom, JwtCustom}
import apikeysteward.services.UuidGenerator
import cats.data.EitherT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxApplicativeErrorId, catsSyntaxApplicativeId, catsSyntaxEitherId, none}
import doobie.ConnectionIO
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.Mockito.{verify, verifyNoInteractions}
import org.mockito.MockitoSugar.{mock, reset, times}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.{JwtAlgorithm, JwtHeader, JwtOptions}

import java.sql.SQLException
import scala.util.{Failure, Success, Try}

class Auth0LoginRepositorySpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DoobieUnitSpec
    with FixedClock
    with BeforeAndAfterEach
    with EitherValues {

  private val uuidGenerator       = mock[UuidGenerator]
  private val auth0LoginDb        = mock[Auth0LoginDb]
  private val auth0LoginConnector = mock[Auth0LoginConnector]
  private val jwtCustom           = mock[JwtCustom]

  private val auth0LoginRepository =
    new Auth0LoginRepository(uuidGenerator, auth0LoginDb, auth0LoginConnector, jwtCustom)(noopTransactor)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(uuidGenerator, auth0LoginDb, auth0LoginConnector, jwtCustom)
  }

  private val jwtHeader: JwtHeader     = JwtHeader()
  private val jwtClaim: JwtClaimCustom = JwtClaimCustom()
  private val jwtVerificationOptions   = new JwtOptions(signature = false, expiration = true)

  private def mockJwtCustomDecodeAll(
      result: Try[(JwtHeader, JwtClaimCustom, String)] = Success((jwtHeader, jwtClaim, accessToken_1))
  ): Unit =
    jwtCustom
      .decodeAll(
        eqTo(accessToken_1),
        eqTo("fakeKey"),
        eqTo(Seq(JwtAlgorithm.RS256)),
        eqTo(jwtVerificationOptions)
      )
      .returns(result)

  private val testException = new RuntimeException("Test Exception")

  "Auth0LoginRepository on getAccessToken" when {

    "should always call Auth0LoginDb" in {
      auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
      mockJwtCustomDecodeAll()

      for {
        _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

        _ = verify(auth0LoginDb).getByTenantDomain(eqTo(tenantDomain_1))
      } yield ()
    }

    "Auth0LoginDb.getByTenantDomain throws exception" should {
      "return failed IO containing this exception" in {
        auth0LoginDb
          .getByTenantDomain(any())
          .returns(testException.raiseError[doobie.ConnectionIO, Option[Auth0LoginEntity.Read]])

        auth0LoginRepository
          .getAccessToken(tenantDomain_1)
          .attempt
          .asserting(res => res shouldBe Left(testException))
      }
    }

    "there is a valid access token in the DB" should {

      "call Auth0LoginDb and JwtCustom" in {
        auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
        mockJwtCustomDecodeAll()

        for {
          _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

          _ = verify(auth0LoginDb).getByTenantDomain(eqTo(tenantDomain_1))
          _ = verify(jwtCustom)
            .decodeAll(
              eqTo(accessToken_1),
              eqTo("fakeKey"),
              eqTo(Seq(JwtAlgorithm.RS256)),
              eqTo(jwtVerificationOptions)
            )
        } yield ()
      }

      "return access token obtained from DB" in {
        auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
        mockJwtCustomDecodeAll()

        auth0LoginRepository
          .getAccessToken(tenantDomain_1)
          .asserting(_ shouldBe Right(auth0LoginEntityRead_1.accessToken))
      }
    }

    "there is no access token in the DB" when {

      "everything works correctly" should {

        "call Auth0LoginConnector, UuidGenerator and Auth0LoginDb" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(auth0LoginEntityRead_1.asRight[Auth0LoginError].pure[ConnectionIO])

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

            _ = verify(auth0LoginDb).getByTenantDomain(eqTo(tenantDomain_1))
            _ = verify(auth0LoginConnector).fetchAccessToken(eqTo(tenantDomain_1))
            _ = verify(uuidGenerator).generateUuid
            _ = verify(auth0LoginDb).upsert(eqTo(auth0LoginEntityWrite_1))
          } yield ()
        }

        "NOT call JwtCustom" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(auth0LoginEntityRead_1.asRight[Auth0LoginError].pure[ConnectionIO])

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

            _ = verifyNoInteractions(jwtCustom)
          } yield ()
        }

        "return access token fetched from Auth0 API" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(auth0LoginEntityRead_1.asRight[Auth0LoginError].pure[ConnectionIO])

          auth0LoginRepository
            .getAccessToken(tenantDomain_1)
            .asserting(_ shouldBe Right(auth0LoginResponse_1.access_token))
        }
      }

      "Auth0LoginConnector returns Left containing Auth0LoginError" should {

        val error: Auth0LoginError = Auth0LoginUpstreamErrorResponse(500, "Upstream Internal Server Error")

        "NOT call UuidGenerator, Auth0LoginDb.upsert or JwtCustom" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.left(IO.pure(error)))

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

            _ = verifyNoInteractions(uuidGenerator, jwtCustom)
            _ = verify(auth0LoginDb, times(0)).upsert(any())
          } yield ()
        }

        "return Left containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.left(IO.pure(error)))

          auth0LoginRepository.getAccessToken(tenantDomain_1).asserting(_ shouldBe Left(error))
        }
      }

      "Auth0LoginConnector returns failed IO" should {

        "NOT call UuidGenerator, Auth0LoginDb.upsert or JwtCustom" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector
            .fetchAccessToken(any())
            .returns(EitherT(IO.raiseError[Either[Auth0LoginError, Auth0LoginResponse]](testException)))

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1).attempt

            _ = verifyNoInteractions(uuidGenerator, jwtCustom)
            _ = verify(auth0LoginDb, times(0)).upsert(any())
          } yield ()
        }

        "return failed IO containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector
            .fetchAccessToken(any())
            .returns(EitherT(IO.raiseError[Either[Auth0LoginError, Auth0LoginResponse]](testException)))

          auth0LoginRepository.getAccessToken(tenantDomain_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }

      "UuidGenerator returns failed IO" should {

        "NOT call Auth0LoginDb.upsert or JwtCustom" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.raiseError(testException))

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1).attempt

            _ = verifyNoInteractions(jwtCustom)
            _ = verify(auth0LoginDb, times(0)).upsert(any())
          } yield ()
        }

        "return failed IO containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.raiseError(testException))

          auth0LoginRepository.getAccessToken(tenantDomain_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }

      "Auth0LoginDb.upsert returns Left containing Auth0LoginError" should {

        val error: Auth0LoginError = Auth0LoginUpsertError(new SQLException("Test SQL Exception"))

        "NOT call JwtCustom" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(error.asLeft[Auth0LoginEntity.Read].pure[ConnectionIO])

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

            _ = verifyNoInteractions(jwtCustom)
          } yield ()
        }

        "return Left containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(error.asLeft[Auth0LoginEntity.Read].pure[ConnectionIO])

          auth0LoginRepository.getAccessToken(tenantDomain_1).asserting(_ shouldBe Left(error))
        }
      }

      "Auth0LoginDb.upsert returns failed IO" should {

        "NOT call JwtCustom" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb
            .upsert(any())
            .returns(testException.raiseError[doobie.ConnectionIO, Either[Auth0LoginError, Auth0LoginEntity.Read]])

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1).attempt

            _ = verifyNoInteractions(jwtCustom)
          } yield ()
        }

        "return Left containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(none[Auth0LoginEntity.Read].pure[ConnectionIO])
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb
            .upsert(any())
            .returns(testException.raiseError[doobie.ConnectionIO, Either[Auth0LoginError, Auth0LoginEntity.Read]])

          auth0LoginRepository.getAccessToken(tenantDomain_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }
    }

    "there is an expired access token in the DB" when {

      val tokenExpiredException = new JwtExpirationException(expiration = expiresIn_1)

      "everything works correctly" should {

        "call JwtCustom, Auth0LoginConnector, UuidGenerator and Auth0LoginDb" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(auth0LoginEntityRead_1.asRight[Auth0LoginError].pure[ConnectionIO])

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

            _ = verify(auth0LoginDb).getByTenantDomain(eqTo(tenantDomain_1))
            _ = verify(jwtCustom)
              .decodeAll(
                eqTo(accessToken_1),
                eqTo("fakeKey"),
                eqTo(Seq(JwtAlgorithm.RS256)),
                eqTo(jwtVerificationOptions)
              )
            _ = verify(auth0LoginConnector).fetchAccessToken(eqTo(tenantDomain_1))
            _ = verify(uuidGenerator).generateUuid
            _ = verify(auth0LoginDb).upsert(eqTo(auth0LoginEntityWrite_1))
          } yield ()
        }

        "return access token fetched from Auth0 API" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(auth0LoginEntityRead_1.asRight[Auth0LoginError].pure[ConnectionIO])

          auth0LoginRepository
            .getAccessToken(tenantDomain_1)
            .asserting(_ shouldBe Right(auth0LoginResponse_1.access_token))
        }
      }

      "Auth0LoginConnector returns Left containing Auth0LoginError" should {

        val error: Auth0LoginError = Auth0LoginUpstreamErrorResponse(500, "Upstream Internal Server Error")

        "NOT call UuidGenerator or Auth0LoginDb.upsert" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.left(IO.pure(error)))

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1)

            _ = verifyNoInteractions(uuidGenerator)
            _ = verify(auth0LoginDb, times(0)).upsert(any())
          } yield ()
        }

        "return Left containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.left(IO.pure(error)))

          auth0LoginRepository.getAccessToken(tenantDomain_1).asserting(_ shouldBe Left(error))
        }
      }

      "Auth0LoginConnector returns failed IO" should {

        "NOT call UuidGenerator or Auth0LoginDb.upsert" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector
            .fetchAccessToken(any())
            .returns(EitherT(IO.raiseError[Either[Auth0LoginError, Auth0LoginResponse]](testException)))

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1).attempt

            _ = verifyNoInteractions(uuidGenerator)
            _ = verify(auth0LoginDb, times(0)).upsert(any())
          } yield ()
        }

        "return failed IO containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector
            .fetchAccessToken(any())
            .returns(EitherT(IO.raiseError[Either[Auth0LoginError, Auth0LoginResponse]](testException)))

          auth0LoginRepository.getAccessToken(tenantDomain_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }

      "UuidGenerator returns failed IO" should {

        "NOT call Auth0LoginDb.upsert " in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.raiseError(testException))

          for {
            _ <- auth0LoginRepository.getAccessToken(tenantDomain_1).attempt

            _ = verify(auth0LoginDb, times(0)).upsert(any())
          } yield ()
        }

        "return failed IO containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.raiseError(testException))

          auth0LoginRepository.getAccessToken(tenantDomain_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }

      "Auth0LoginDb.upsert returns Left containing Auth0LoginError" should {
        "return Left containing this error" in {
          val error: Auth0LoginError = Auth0LoginUpsertError(new SQLException("Test SQL Exception"))

          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb.upsert(any()).returns(error.asLeft[Auth0LoginEntity.Read].pure[ConnectionIO])

          auth0LoginRepository.getAccessToken(tenantDomain_1).asserting(_ shouldBe Left(error))
        }
      }

      "Auth0LoginDb.upsert returns failed IO" should {
        "return Left containing this error" in {
          auth0LoginDb.getByTenantDomain(any()).returns(Option(auth0LoginEntityRead_1).pure[ConnectionIO])
          mockJwtCustomDecodeAll(Failure(tokenExpiredException))
          auth0LoginConnector.fetchAccessToken(any()).returns(EitherT.right(IO.pure(auth0LoginResponse_1)))
          uuidGenerator.generateUuid.returns(IO.pure(auth0LoginDbId_1))
          auth0LoginDb
            .upsert(any())
            .returns(testException.raiseError[doobie.ConnectionIO, Either[Auth0LoginError, Auth0LoginEntity.Read]])

          auth0LoginRepository.getAccessToken(tenantDomain_1).attempt.asserting(_ shouldBe Left(testException))
        }
      }
    }
  }

}
