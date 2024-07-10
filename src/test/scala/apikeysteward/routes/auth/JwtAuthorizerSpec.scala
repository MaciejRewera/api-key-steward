package apikeysteward.routes.auth

import apikeysteward.base.FixedJwtCustom
import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.AuthTestData._
import apikeysteward.routes.auth.JwtAuthorizer.buildNoRequiredPermissionsUnauthorizedErrorInfo
import apikeysteward.routes.auth.JwtDecoder._
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError.MissingKeyIdFieldError
import apikeysteward.routes.auth.PublicKeyGenerator._
import apikeysteward.routes.auth.model.JsonWebToken
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

class JwtAuthorizerSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with BeforeAndAfterEach
    with EitherValues
    with FixedJwtCustom {

  private val jwtDecoder = mock[JwtDecoder]

  private val jwtAuthorizer = new JwtAuthorizer(jwtDecoder)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(jwtDecoder)
  }

  private val testException = new RuntimeException("Test Exception")

  "JwtAuthorizer on authorised" when {

    "should always call JwtDecoder providing access token" in {
      jwtDecoder.decode(any[String]) returns IO.pure(Right(jwtWithMockedSignature))

      for {
        _ <- jwtAuthorizer.authorised(jwtString)

        _ = verify(jwtDecoder).decode(eqTo(jwtString))
      } yield ()
    }

    "JwtDecoder returns Right containing JsonWebToken" should {
      "return Right containing JsonWebToken returned from JwtDecoder" in {
        jwtDecoder.decode(any[String]) returns IO.pure(Right(jwtWithMockedSignature))

        jwtAuthorizer.authorised(jwtString).asserting(_ shouldBe Right(jwtWithMockedSignature))
      }
    }

    "JwtDecoder returns Left containing JwtDecoderError" should {
      "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

        "the error is DecodingError" in {
          val error = DecodingError(new JwtExpirationException(Instant.now().toEpochMilli))
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          jwtAuthorizer.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }

        "the error is MissingKeyIdFieldError" in {
          val error = ValidationError(MissingKeyIdFieldError)
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          jwtAuthorizer.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }

        "the error is MatchingJwkNotFoundError" in {
          val error = MatchingJwkNotFoundError(kid_1)
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          jwtAuthorizer.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }

        "the error is PublicKeyGenerationError" in {
          val failureReasons = Seq(
            AlgorithmNotSupportedError("RS256", "HS256"),
            KeyTypeNotSupportedError("RSA", "HSA"),
            KeyUseNotSupportedError("sig", "no-use")
          )
          val error = PublicKeyGenerationError(failureReasons)
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          jwtAuthorizer.authorised(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value.error shouldBe "Invalid Credentials"
            result.left.value.errorDetail.get shouldBe "Cannot generate Public Key because: ['Algorithm HS256 is not supported. Please use RS256.', 'Key Type HSA is not supported. Please use RSA.', 'Public Key Use no-use is not supported. Please use sig.']."
          }
        }
      }
    }

    "JwtDecoder returns failed IO" should {
      "return this failed IO" in {
        jwtDecoder.decode(any[String]) returns IO.raiseError(testException)

        jwtAuthorizer.authorised(jwtString).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe testException
        }
      }
    }
  }

  "JwtAuthorizer on authorisedWithPermissions" when {

    "should always call JwtDecoder providing access token" in {
      jwtDecoder.decode(any[String]) returns IO.pure(Right(jwtWithMockedSignature))

      for {
        _ <- jwtAuthorizer.authorisedWithPermissions(Set(permissionRead_1))(jwtString)

        _ = verify(jwtDecoder).decode(eqTo(jwtString))
      } yield ()
    }

    "JwtDecoder returns Right containing JsonWebToken" when {

      "there are no permissions required" should {

        val subjectFuncNoPermissions: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtAuthorizer.authorisedWithPermissions()

        "return Right containing JsonWebToken returned from JwtDecoder" when {

          "provided token contains NO permissions" in {
            val tokenPermissions = None
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val accessToken: String =
              jwtCustom.encode(jwtHeader, jwtClaim.copy(permissions = tokenPermissions), privateKey)

            subjectFuncNoPermissions(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }

          "provided token contains permissions" in {
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jwtWithMockedSignature))

            subjectFuncNoPermissions(jwtString).asserting(_ shouldBe Right(jwtWithMockedSignature))
          }
        }
      }

      "there is a single permission required" should {

        val requiredPermissions = Set(permissionRead_1)
        val subjectFuncSinglePermission: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtAuthorizer.authorisedWithPermissions(requiredPermissions)

        "return Right containing JsonWebToken returned from JwtDecoder" when {

          "provided token contains this single permission" in {
            val tokenPermissions = Some(requiredPermissions)
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncSinglePermission(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }

          "provided token contains this permission together with other permissions" in {
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jwtWithMockedSignature))

            subjectFuncSinglePermission(jwtString).asserting(_ shouldBe Right(jwtWithMockedSignature))
          }
        }

        "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

          "provided token contains NO permissions" in {
            val tokenPermissions = None
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val accessToken: String =
              jwtCustom.encode(jwtHeader, jwtClaim.copy(permissions = tokenPermissions), privateKey)

            subjectFuncSinglePermission(accessToken).asserting(
              _ shouldBe Left(buildNoRequiredPermissionsUnauthorizedErrorInfo(requiredPermissions, Set.empty))
            )
          }

          "provided token contains different permissions" in {
            val tokenPermissions = Some(Set(permissionWrite_1, permissionRead_2))
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val accessToken: String =
              jwtCustom.encode(jwtHeader, jwtClaim.copy(permissions = tokenPermissions), privateKey)

            subjectFuncSinglePermission(accessToken).asserting(
              _ shouldBe Left(
                buildNoRequiredPermissionsUnauthorizedErrorInfo(
                  requiredPermissions,
                  Set(permissionWrite_1, permissionRead_2)
                )
              )
            )
          }
        }
      }

      "there are multiple permissions required" should {

        val requiredPermissions = Set(permissionRead_1, permissionWrite_1)
        val subjectFuncMultiplePermissions: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtAuthorizer.authorisedWithPermissions(requiredPermissions)

        "return Right containing JsonWebToken returned from JwtDecoder" when {

          "provided token contains all required permissions" in {
            val tokenPermissions = Some(requiredPermissions)
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }

          "provided token contains all required permissions together with other permissions" in {
            val tokenPermissions = Some(requiredPermissions ++ Set(permissionRead_2, permissionWrite_2))
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(_ shouldBe Right(jsonWebToken))
          }
        }

        "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

          "provided token contains NO permissions" in {
            val tokenPermissions = None
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(buildNoRequiredPermissionsUnauthorizedErrorInfo(requiredPermissions, Set.empty))
            )
          }

          "provided token contains different permissions" in {
            val tokenPermissions = Some(Set(permissionRead_2, permissionWrite_2))
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(
                buildNoRequiredPermissionsUnauthorizedErrorInfo(requiredPermissions, tokenPermissions.get)
              )
            )
          }

          "provided token contains only a subset of required permissions" in {
            val tokenPermissions = Some(Set(permissionRead_1))
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(
                buildNoRequiredPermissionsUnauthorizedErrorInfo(requiredPermissions, tokenPermissions.get)
              )
            )
          }

          "provided token contains only a subset of required permissions together with other permissions" in {
            val tokenPermissions = Some(Set(permissionRead_1, permissionRead_2, permissionWrite_2))
            val jsonWebToken = jwtWithMockedSignature.copy(claim = jwtClaim.copy(permissions = tokenPermissions))
            jwtDecoder.decode(any[String]) returns IO.pure(Right(jsonWebToken))

            val jwtClaimSinglePermission = jwtClaim.copy(permissions = tokenPermissions)
            val accessToken: String = jwtCustom.encode(jwtHeader, jwtClaimSinglePermission, privateKey)

            subjectFuncMultiplePermissions(accessToken).asserting(
              _ shouldBe Left(
                buildNoRequiredPermissionsUnauthorizedErrorInfo(requiredPermissions, tokenPermissions.get)
              )
            )
          }
        }
      }
    }

    "JwtDecoder returns Left containing JwtDecoderError" should {
      "return Left containing ErrorInfo with detail explaining the reason why token decoding failed" when {

        val requiredPermissions = Set(permissionRead_1)
        val subjectFuncSinglePermission: String => IO[Either[ErrorInfo, JsonWebToken]] =
          jwtAuthorizer.authorisedWithPermissions(requiredPermissions)

        "the error is DecodingError" in {
          val error = DecodingError(new JwtExpirationException(Instant.now().toEpochMilli))
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }

        "the error is MissingKeyIdFieldError" in {
          val error = ValidationError(MissingKeyIdFieldError)
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }

        "the error is MatchingJwkNotFoundError" in {
          val error = MatchingJwkNotFoundError(kid_1)
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }

        "the error is PublicKeyGenerationError" in {
          val failureReasons = Seq(
            AlgorithmNotSupportedError("RS256", "HS256"),
            KeyTypeNotSupportedError("RSA", "HSA"),
            KeyUseNotSupportedError("sig", "no-use")
          )
          val error = PublicKeyGenerationError(failureReasons)
          jwtDecoder.decode(any[String]) returns IO.pure(Left(error))

          subjectFuncSinglePermission(jwtString).asserting { result =>
            result.isLeft shouldBe true
            result.left.value shouldBe ErrorInfo.unauthorizedErrorInfo(Some(error.message))
          }
        }
      }
    }

    "JwtDecoder returns failed IO" should {
      "return this failed IO" in {
        jwtDecoder.decode(any[String]) returns IO.raiseError(testException)

        jwtAuthorizer.authorisedWithPermissions(Set(permissionRead_1))(jwtString).attempt.asserting { result =>
          result.isLeft shouldBe true
          result.left.value shouldBe testException
        }
      }
    }
  }

}
