package apikeysteward.routes.auth

import apikeysteward.routes.ErrorInfo
import apikeysteward.routes.auth.JwtOps._
import apikeysteward.routes.auth.model.JsonWebToken
import cats.implicits.catsSyntaxEitherId

class JwtOps {

  def extractUserId(jwt: JsonWebToken): Either[ErrorInfo, String] =
    jwt.claim.userId.map(_.trim) match {
      case None     => SubFieldNotProvidedErrorInfo.asLeft
      case Some("") => SubFieldIsEmptyErrorInfo.asLeft

      case Some(userId) => userId.asRight
    }
}

object JwtOps {

  private val SubFieldNotProvidedErrorInfo: ErrorInfo =
    ErrorInfo.unauthorizedErrorInfo(Some("'sub' field is not present in provided JWT."))

  private val SubFieldIsEmptyErrorInfo: ErrorInfo =
    ErrorInfo.unauthorizedErrorInfo(Some("'sub' field in provided JWT cannot be empty."))
}
