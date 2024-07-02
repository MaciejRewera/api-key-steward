package apikeysteward.routes.auth

import apikeysteward.routes.ErrorInfo
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JwtOpsSpec extends AnyWordSpec with Matchers with EitherValues {

  private val jwtOps: JwtOps = new JwtOps

  "JwtOps on extractUserId" should {

    "return Right containing userId" when {
      "provided with JsonWebToken containing non-empty userId field" in {
        val jwt = AuthTestData.jwtWithMockedSignature
        val expectedUserId = AuthTestData.jwtWithMockedSignature.claim.userId.get

        jwtOps.extractUserId(jwt) shouldBe Right(expectedUserId)
      }
    }

    "return Left containing ErrorInfo" when {

      "provided with JsonWebToken without the userId field" in {
        val jwt = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(userId = None)
        )
        val expectedErrorInfo = ErrorInfo.unauthorizedErrorInfo(Some("'sub' field is not present in provided JWT."))

        jwtOps.extractUserId(jwt) shouldBe Left(expectedErrorInfo)
      }

      "provided with JsonWebToken containing empty userId field" in {
        val jwt = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(userId = Some(""))
        )
        val expectedErrorInfo = ErrorInfo.unauthorizedErrorInfo(Some("'sub' field in provided JWT cannot be empty."))

        jwtOps.extractUserId(jwt) shouldBe Left(expectedErrorInfo)
      }

      "provided with JsonWebToken containing userId field with only white characters" in {
        val jwt = AuthTestData.jwtWithMockedSignature.copy(
          claim = AuthTestData.jwtClaim.copy(userId = Some("   \n   \n\n "))
        )
        val expectedErrorInfo = ErrorInfo.unauthorizedErrorInfo(Some("'sub' field in provided JWT cannot be empty."))

        jwtOps.extractUserId(jwt) shouldBe Left(expectedErrorInfo)
      }
    }
  }

}
