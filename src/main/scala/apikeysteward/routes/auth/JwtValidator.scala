package apikeysteward.routes.auth

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtValidator.{AccessToken, Permission, buildUnauthorizedErrorInfo}
import apikeysteward.routes.auth.model.JsonWebToken
import cats.effect.IO
import cats.implicits.catsSyntaxEitherId

class JwtValidator(jwtDecoder: JwtDecoder) {

  def authorised(accessToken: AccessToken): IO[Either[ErrorInfo, JsonWebToken]] =
    jwtDecoder.decode(accessToken).map(_.asRight).recover { case error =>
      ErrorInfo.unauthorizedErrorInfo(Some(error.getMessage)).asLeft
    }

  def authorisedWithPermissions(permissions: Set[Permission] = Set.empty)(
      accessToken: AccessToken
  ): IO[Either[ErrorInfo, JsonWebToken]] =
    authorised(accessToken).map {
      case Right(jwt)  => validatePermissions(permissions)(jwt)
      case Left(error) => Left(error)
    }

  private def validatePermissions(
      requiredPermissions: Set[Permission]
  )(jwt: JsonWebToken): Either[ErrorInfo, JsonWebToken] = {
    val tokenPermissions = extractTokenPermissions(jwt)

    if (requiredPermissions.subsetOf(tokenPermissions))
      Right(jwt)
    else
      Left(buildUnauthorizedErrorInfo(requiredPermissions, tokenPermissions))
  }

  private def extractTokenPermissions(jwt: JsonWebToken): Set[Permission] = jwt.jwtClaim.permissions match {
    case Some(tokenPermissions) => tokenPermissions
    case None                   => Set.empty
  }

}

object JwtValidator {
  type AccessToken = String
  type Permission = String

  def buildUnauthorizedErrorInfo(requiredPermissions: Set[Permission], tokenPermissions: Set[Permission]): ErrorInfo =
    ErrorInfo.unauthorizedErrorInfo(Some(buildErrorMessage(requiredPermissions, tokenPermissions)))

  private def buildErrorMessage(requiredPermissions: Set[Permission], providedPermissions: Set[Permission]): String =
    s"Provided token does not contain all required permissions: ${requiredPermissions.mkString("[", ", ", "]")}. Permissions provided in the token: ${providedPermissions
      .mkString("[", ", ", "]")}."

}
