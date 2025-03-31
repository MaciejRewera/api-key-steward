package apikeysteward.routes.auth.model

import apikeysteward.config.JwtConfig
import cats.implicits.catsSyntaxEitherId
import io.circe.Decoder.Result
import io.circe.syntax.EncoderOps
import io.circe.{Codec, HCursor, Json}

case class JwtClaimCustom(
    content: String = "{}",
    issuer: Option[String] = None,
    subject: Option[String] = None,
    audience: Option[Set[String]] = None,
    expiration: Option[Long] = None,
    notBefore: Option[Long] = None,
    issuedAt: Option[Long] = None,
    jwtId: Option[String] = None,
    permissions: Option[Set[String]] = None,
    userId: Option[String] = None
)

object JwtClaimCustom {

  implicit def codec(implicit config: JwtConfig): Codec[JwtClaimCustom] = new Codec[JwtClaimCustom] {

    override def apply(claim: JwtClaimCustom): Json = {
      val userIdFieldOpt: Option[(String, Option[Json])] = config.userIdClaimName.flatMap {
        case ""        => None
        case claimName => Some(claimName -> claim.userId.orElse(Some("")).map(Json.fromString))
      }

      val definedFields = (
        Seq(
          "iss"         -> claim.issuer.map(Json.fromString),
          "sub"         -> claim.subject.map(Json.fromString),
          "aud"         -> claim.audience.map(set => Json.fromValues(set.map(Json.fromString))),
          "exp"         -> claim.expiration.map(Json.fromLong),
          "nbf"         -> claim.notBefore.map(Json.fromLong),
          "iat"         -> claim.issuedAt.map(Json.fromLong),
          "jti"         -> claim.jwtId.map(Json.fromString),
          "permissions" -> claim.permissions.map(set => Json.fromValues(set.map(Json.fromString)))
        ) ++ userIdFieldOpt
      ).collect { case (key, Some(value)) =>
        key -> value
      }

      Json.obj(definedFields: _*)
    }

    override def apply(cursor: HCursor): Result[JwtClaimCustom] =
      extractUserId(cursor).flatMap { userId =>
        JwtClaimCustom(
          content = cursor.top.asJson.noSpaces,
          issuer = cursor.get[String]("iss").toOption,
          subject = cursor.get[String]("sub").toOption,
          audience = cursor
            .get[Set[String]]("aud")
            .toOption
            .orElse(cursor.get[String]("aud").map(Set(_)).toOption),
          expiration = cursor.get[Long]("exp").toOption,
          notBefore = cursor.get[Long]("nbf").toOption,
          issuedAt = cursor.get[Long]("iat").toOption,
          jwtId = cursor.get[String]("jti").toOption,
          permissions = cursor
            .get[Set[String]]("permissions")
            .toOption
            .orElse(cursor.get[String]("permissions").map(Set(_)).toOption),
          userId = userId
        ).asRight
      }

    private def extractUserId(cursor: HCursor): Result[Option[String]] =
      (
        config.userIdClaimName match {
          case None | Some("") => cursor.get[String]("sub")
          case Some(fieldName) => cursor.get[String](fieldName)
        }
      ).map(Option(_))
  }

}
