package apikeysteward.routes.auth.model

import apikeysteward.base.FixedJwtCustom
import apikeysteward.config.JwtConfig
import apikeysteward.routes.auth.AuthTestData._
import io.circe.{DecodingFailure, HCursor, Json, parser}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt

class JwtClaimCustomSpec extends AnyWordSpec with Matchers with FixedJwtCustom with EitherValues {

  private def buildJwtConfig(userIdClaimName: Option[String]): JwtConfig = JwtConfig(
    allowedIssuers = Set.empty,
    allowedAudiences = Set.empty,
    maxAge = None,
    userIdClaimName = userIdClaimName,
    requireExp = true,
    requireNbf = false,
    requireIat = true,
    requireIss = true,
    requireAud = true
  )

  "JwtClaimCustom on codec.apply(:JwtClaimCustom)" should {

    "return Json with permissions field" when {

      "JwtClaimCustom.permission field contains empty Option" in {
        val jwtConfig: JwtConfig = buildJwtConfig(None)
        val jwtClaimCustom       = jwtClaim.copy(permissions = None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

        val permissions = (result \\ "permissions").flatMap(_.asArray).flatten.flatMap(_.asString)
        permissions shouldBe List.empty[Json]
      }

      "JwtClaimCustom.permission field contains Option with empty Set" in {
        val jwtConfig: JwtConfig = buildJwtConfig(None)
        val jwtClaimCustom       = jwtClaim.copy(permissions = Some(Set.empty[String]))

        val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

        val permissions = (result \\ "permissions").flatMap(_.asArray).flatten.flatMap(_.asString)
        permissions shouldBe List.empty[Json]
      }

      "JwtClaimCustom.permission field contains Option with non-empty Set" in {
        val jwtConfig: JwtConfig = buildJwtConfig(None)
        val jwtClaimCustom       = jwtClaim

        val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

        val permissions = (result \\ "permissions").flatMap(_.asArray).flatten.flatMap(_.asString)
        permissions.size shouldBe 2
        permissions should contain theSameElementsAs List(permissionRead_1, permissionWrite_1)
      }
    }

    "return Json without any extra field containing user ID" when {

      "JwtConfig contains empty Option for userIdClaimName" when {

        "userId field is an empty Option" in {
          val jwtConfig: JwtConfig = buildJwtConfig(None)
          val jwtClaimCustom       = jwtClaim.copy(userId = None)

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          result shouldBe parser.parse(jwtClaimJsonString).value
        }

        "userId field is a non-empty Option" in {
          val jwtConfig: JwtConfig = buildJwtConfig(None)
          val jwtClaimCustom       = jwtClaim

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          result shouldBe parser.parse(jwtClaimJsonString).value
        }
      }

      "JwtConfig contains non-empty Option containing empty String for userIdClaimName" when {

        "userId field is an empty Option" in {
          val jwtConfig: JwtConfig = buildJwtConfig(Some(""))
          val jwtClaimCustom       = jwtClaim.copy(userId = None)

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          result shouldBe parser.parse(jwtClaimJsonString).value
        }

        "userId field is a non-empty Option" in {
          val jwtConfig: JwtConfig = buildJwtConfig(Some(""))
          val jwtClaimCustom       = jwtClaim

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          result shouldBe parser.parse(jwtClaimJsonString).value
        }
      }
    }

    "return Json with an extra field containing user ID" when {

      "JwtConfig contains non-empty Option for userIdClaimName" when {

        val userIdClaimName = "test-user-id-claim-name"

        "userId field contains empty Option" in {
          val jwtConfig: JwtConfig = buildJwtConfig(Some(userIdClaimName))
          val jwtClaimCustom       = jwtClaim.copy(userId = None)

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          val userId = (result \\ userIdClaimName).flatMap(_.asString)
          userId.size shouldBe 1
          userId.head shouldBe ""
        }

        "userId field contains non-empty Option with empty String" in {
          val jwtConfig: JwtConfig = buildJwtConfig(Some(userIdClaimName))
          val jwtClaimCustom       = jwtClaim.copy(userId = Some(""))

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          val userId = (result \\ userIdClaimName).flatMap(_.asString)
          userId.size shouldBe 1
          userId.head shouldBe ""
        }

        "userId field contains non-empty Option with non-empty String" in {
          val jwtConfig: JwtConfig = buildJwtConfig(Some(userIdClaimName))
          val jwtClaimCustom       = jwtClaim.copy(userId = Some("test-user-id-1"))

          val result = JwtClaimCustom.codec(jwtConfig).apply(jwtClaimCustom)

          val userId = (result \\ userIdClaimName).flatMap(_.asString)
          userId.size shouldBe 1
          userId.head shouldBe "test-user-id-1"
        }
      }
    }
  }

  "JwtClaimCustom on codec.apply(:HCursor)" should {

    "return JwtClaimCustom with content field containing the whole Json" when {
      "provided with a token with claims which are not used" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionWrite_1"],
             |  "unknownClaim-1": "unknown claim value 1",
             |  "unknownClaim-2": "unknown claim value 2",
             |  "unknownClaim-3": "unknown claim value 3"
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        val expectedResult = jwtClaim.copy(
          content = jwtClaimJson.noSpaces
        )
        result shouldBe Right(expectedResult)
      }
    }

    "return JwtClaimCustom with permissions field containing empty Option" when {
      "provided with a token without permissions claim" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds}
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        val expectedResult = jwtClaim.copy(
          content = jwtClaimJson.noSpaces,
          permissions = None
        )
        result shouldBe Right(expectedResult)
      }
    }

    "return JwtClaimCustom with permissions field containing empty Set" when {
      "provided with a token with empty permissions claim" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": [],
             |  "azp": "qwerty"
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        val expectedResult = jwtClaim.copy(
          content = jwtClaimJson.noSpaces,
          permissions = Some(Set.empty[String])
        )
        result shouldBe Right(expectedResult)
      }
    }

    "return JwtClaimCustom with permissions field containing Set with claims" when {

      "provided with a token with permissions claim" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionWrite_1"]
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        val expectedResult = jwtClaim.copy(
          content = jwtClaimJson.noSpaces
        )
        result shouldBe Right(expectedResult)
      }

      "provided with a token with permissions claim containing repeated values" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"]
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        val expectedResult = jwtClaim.copy(
          content = jwtClaimJson.noSpaces
        )
        result shouldBe Right(expectedResult)
      }
    }

    "return JwtClaimCustom with userId value equal to subject value" when {

      "JwtConfig contains empty Option for userIdClaimName" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"]
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(None)

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        result.isRight shouldBe true
        result.value.subject shouldBe Some(subject_1)
        result.value.userId shouldBe Some(subject_1)
      }

      "JwtConfig contains Option with empty String for userIdClaimName" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"]
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(Some(""))

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        result.isRight shouldBe true
        result.value.subject shouldBe Some(subject_1)
        result.value.userId shouldBe Some(subject_1)
      }

      "JwtConfig contains 'sub' value for userIdClaimName" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"]
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(Some("sub"))

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        result.isRight shouldBe true
        result.value.subject shouldBe Some(subject_1)
        result.value.userId shouldBe Some(subject_1)
      }
    }

    "return JwtClaimCustom with userId value obtained from claim with provided name" when {
      "JwtConfig contains Option with non-empty String for userIdClaimName" when {

        "the claim is non-empty" in {
          val userId = "test-user-id-1"
          val jwtClaimJsonString: String =
            s"""{
               |  "iss": "$issuer_1",
               |  "sub": "$subject_1",
               |  "aud": ["$audience_1", "$audience_2"],
               |  "exp": ${now.plus(5.minutes).toSeconds},
               |  "nbf": ${now.minus(1.minute).toSeconds},
               |  "iat": ${now.minus(1.minute).toSeconds},
               |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"],
               |  "test-user-id-claim-name": "$userId"
               |}
               |""".stripMargin
          val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
          val jwtConfig: JwtConfig = buildJwtConfig(Some("test-user-id-claim-name"))

          val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

          result.isRight shouldBe true
          result.value.subject shouldBe Some(subject_1)
          result.value.userId shouldBe Some(userId)
        }

        "the claim is an empty String" in {
          val jwtClaimJsonString: String =
            s"""{
               |  "iss": "$issuer_1",
               |  "sub": "$subject_1",
               |  "aud": ["$audience_1", "$audience_2"],
               |  "exp": ${now.plus(5.minutes).toSeconds},
               |  "nbf": ${now.minus(1.minute).toSeconds},
               |  "iat": ${now.minus(1.minute).toSeconds},
               |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"],
               |  "test-user-id-claim-name": ""
               |}
               |""".stripMargin
          val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
          val jwtConfig: JwtConfig = buildJwtConfig(Some("test-user-id-claim-name"))

          val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

          result.isRight shouldBe true
          result.value.subject shouldBe Some(subject_1)
          result.value.userId shouldBe Some("")
        }
      }
    }

    "return Left containing DecodingFailure" when {
      "JwtConfig contains Option with non-empty String for userIdClaimName and this claim does not exist in provided token" in {
        val jwtClaimJsonString: String =
          s"""{
             |  "iss": "$issuer_1",
             |  "sub": "$subject_1",
             |  "aud": ["$audience_1", "$audience_2"],
             |  "exp": ${now.plus(5.minutes).toSeconds},
             |  "nbf": ${now.minus(1.minute).toSeconds},
             |  "iat": ${now.minus(1.minute).toSeconds},
             |  "permissions": ["$permissionRead_1", "$permissionRead_1", "$permissionRead_1", "$permissionWrite_1"]
             |}
             |""".stripMargin
        val jwtClaimJson: Json   = parser.parse(jwtClaimJsonString).value
        val jwtConfig: JwtConfig = buildJwtConfig(Some("test-user-id-claim-name"))

        val result = JwtClaimCustom.codec(jwtConfig).apply(HCursor.fromJson(jwtClaimJson))

        result.isLeft shouldBe true
        result.left.value shouldBe a[DecodingFailure]
        result.left.value.getMessage shouldBe "DecodingFailure at .test-user-id-claim-name: Missing required field"
      }
    }
  }

}
