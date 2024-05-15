package apikeysteward.routes.auth

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.JwtValidator.buildUnauthorizedErrorInfo
import apikeysteward.routes.auth.model.{JsonWebToken, JwtCustom}
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar.{mock, reset, verify}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import pdi.jwt.exceptions.JwtExpirationException

import java.time.Instant

class JwtValidatorSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach with EitherValues {

  private val jwtDecoder = mock[JwtDecoder]

  private val jwtValidator = new JwtValidator(jwtDecoder)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwtDecoder)
  }

  "JwtValidator on authorised" when {

    "should always call JwtDecoder providing access token" in {
      jwtDecoder.decode(any[String]) returns IO.pure(jwtWithMockedSignature)

      for {
        _ <- jwtValidator.authorised(jwtString)

        _ = verify(jwtDecoder).decode(eqTo(jwtString))
      } yield ()
    }

    "JwtDecoder returns successful IO" should {
      "return Right containing JsonWebToken returned from JwtDecoder" in {
        jwtDecoder.decode(any[String]) returns IO.pure(jwtWithMockedSignature)

        jwtValidator.authorised(jwtString).asserting(_ shouldBe Right(jwtWithMockedSignature))
      }
    }

    "JwtDecoder returns failed IO" should {

      "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

        "the failed IO contains JwtExpirationException" in {
          val exception = new JwtExpirationException(Instant.now().toEpochMilli)
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          jwtValidator.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }

        "the failed IO contains IllegalArgumentException (no kid field in provided JWT)" in {
          val exception = new IllegalArgumentException("Key ID (kid) field is not provided in token: [test-token]")
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          jwtValidator.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }

        "the failed IO contains NoSuchElementException (cannot find JWK with kid from JWT)" in {
          val exception = new NoSuchElementException("Cannot find JWK with kid: [test-key-id].")
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          jwtValidator.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }

        "the failed IO contains IllegalArgumentException (cannot generate Public Key for multiple reasons)" in {
          val failureReasons = Seq("Here are reasons", "Why PubKey generation failed")
          val exception = new IllegalArgumentException(
            s"Cannot generate Public Key because: ${failureReasons.mkString("[", ", ", "]")}. Provided JWK: [test-jwk]"
          )
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          jwtValidator.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }
      }
    }
  }

  "JwtValidator on authorisedWithPermissions" when {

    "should always call JwtDecoder providing access token" in {
      jwtDecoder.decode(any[String]) returns IO.pure(jwtWithMockedSignature)

      for {
        _ <- jwtValidator.authorisedWithPermissions(Set(permissionRead_1))(jwtString)

        _ = verify(jwtDecoder).decode(eqTo(jwtString))
      } yield ()
    }

    "JwtDecoder returns successful IO" when {

      "there are no permissions required" should {

        val subjectFuncNoPermissions: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtValidator.authorisedWithPermissions()

        "return Right containing JsonWebToken returned from JwtDecoder" when {

          "provided token contains NO permissions" in {
            val tokenPermissions = None
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val accessToken: String =
              JwtCustom.encode(jwtHeader, jwtClaim.copy(permissions = tokenPermissions), privateKey)

            subjectFuncNoPermissions(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }

          "provided token contains permissions" in {
            jwtDecoder.decode(any[String]) returns IO.pure(jwtWithMockedSignature)

            subjectFuncNoPermissions(jwtString).asserting(_ shouldBe Right(jwtWithMockedSignature))
          }
        }
      }

      "there is a single permission required" should {

        val requiredPermissions = Set(permissionRead_1)
        val subjectFuncSinglePermission: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtValidator.authorisedWithPermissions(requiredPermissions)

        "return Right containing JsonWebToken returned from JwtDecoder" when {

          "provided token contains this single permission" in {
            val tokenPermissions = Some(requiredPermissions)
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncSinglePermission(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }

          "provided token contains this permission together with other permissions" in {
            jwtDecoder.decode(any[String]) returns IO.pure(jwtWithMockedSignature)

            subjectFuncSinglePermission(jwtString).asserting(_ shouldBe Right(jwtWithMockedSignature))
          }
        }

        "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

          "provided token contains NO permissions" in {
            val tokenPermissions = None
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val accessToken: String =
              JwtCustom.encode(jwtHeader, jwtClaim.copy(permissions = tokenPermissions), privateKey)

            subjectFuncSinglePermission(accessToken).asserting(
              _ shouldBe Left(buildUnauthorizedErrorInfo(requiredPermissions, Set.empty))
            )
          }

          "provided token contains different permissions" in {
            val tokenPermissions = Some(Set(permissionWrite_1, permissionRead_2))
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val accessToken: String =
              JwtCustom.encode(jwtHeader, jwtClaim.copy(permissions = tokenPermissions), privateKey)

            subjectFuncSinglePermission(accessToken).asserting(
              _ shouldBe Left(buildUnauthorizedErrorInfo(requiredPermissions, Set(permissionWrite_1, permissionRead_2)))
            )
          }
        }
      }

      "there are multiple permissions required" should {

        val requiredPermissions = Set(permissionRead_1, permissionWrite_1)
        val subjectFuncMultiplePermissions: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtValidator.authorisedWithPermissions(requiredPermissions)

        "return Right containing JsonWebToken returned from JwtDecoder" when {

          "provided token contains all required permissions" in {
            val tokenPermissions = Some(requiredPermissions)
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }

          "provided token contains all required permissions together with other permissions" in {
            val tokenPermissions = Some(requiredPermissions ++ Set(permissionRead_2, permissionWrite_2))
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }
        }

        "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

          "provided token contains NO permissions" in {
            val tokenPermissions = None
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(buildUnauthorizedErrorInfo(requiredPermissions, Set.empty))
            )
          }

          "provided token contains different permissions" in {
            val tokenPermissions = Some(Set(permissionRead_2, permissionWrite_2))
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(buildUnauthorizedErrorInfo(requiredPermissions, tokenPermissions.get))
            )
          }

          "provided token contains only a subset of required permissions" in {
            val tokenPermissions = Some(Set(permissionRead_1))
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(buildUnauthorizedErrorInfo(requiredPermissions, tokenPermissions.get))
            )
          }

          "provided token contains only a subset of required permissions together with other permissions" in {
            val tokenPermissions = Some(Set(permissionRead_1, permissionRead_2, permissionWrite_2))
            val jsonWebToken = jwtWithMockedSignature.copy(jwtClaim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(jsonWebToken)

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = JwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(buildUnauthorizedErrorInfo(requiredPermissions, tokenPermissions.get))
            )
          }
        }
      }
    }

    "JwtDecoder returns failed IO" should {

      val requiredPermissions = Set(permissionRead_1)
      val subjectFuncSinglePermission: String => IO[Either[ErrorInfo, JsonWebToken]] =
        jwtValidator.authorisedWithPermissions(requiredPermissions)

      "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

        "the failed IO contains JwtExpirationException" in {
          val exception = new JwtExpirationException(Instant.now().toEpochMilli)
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }

        "the failed IO contains IllegalArgumentException (no kid field in provided JWT)" in {
          val exception = new IllegalArgumentException("Key ID (kid) field is not provided in token: [test-token]")
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }

        "the failed IO contains NoSuchElementException (cannot find JWK with kid from JWT)" in {
          val exception = new NoSuchElementException("Cannot find JWK with kid: [test-key-id].")
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }

        "the failed IO contains IllegalArgumentException (cannot generate Public Key for multiple reasons)" in {
          val failureReasons = Seq("Here are reasons", "Why PubKey generation failed")
          val exception = new IllegalArgumentException(
            s"Cannot generate Public Key because: ${failureReasons.mkString("[", ", ", "]")}. Provided JWK: [test-jwk]"
          )
          jwtDecoder.decode(any[String]) returns IO.raiseError(exception)

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(exception.getMessage))
          }
        }
      }
    }
  }

}
