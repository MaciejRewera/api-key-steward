package apikeysteward.routes

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ErrorInfo(error: String, errorDetail: Option[String])

object ErrorInfo {
  implicit val codec: Codec[ErrorInfo] = deriveCodec[ErrorInfo]

  object Errors {
    val InternalServerError = "Internal Server Error"
    val BadRequest = "Bad Request"
    val Forbidden = "Access Denied"
    val Unauthorized = "Invalid Credentials"
    val NotFound = "Not Found"
  }

  def internalServerErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = Errors.InternalServerError,
    errorDetail = detail
  )

  def badRequestErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = Errors.BadRequest,
    errorDetail = detail
  )

  def forbiddenErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = Errors.Forbidden,
    errorDetail = detail
  )

  def unauthorizedErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = Errors.Unauthorized,
    errorDetail = detail
  )

  def notFoundErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = Errors.NotFound,
    errorDetail = detail
  )
}
