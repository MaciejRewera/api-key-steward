package apikeysteward.routes

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ErrorInfo(error: String, errorDetail: Option[String])

object ErrorInfo {
  implicit val codec: Codec[ErrorInfo] = deriveCodec[ErrorInfo]

  object ErrorDescriptions {
    val InternalServerError = "Internal Server Error"
    val BadRequest = "Bad Request"
    val Forbidden = "Access Denied"
    val Unauthorized = "Invalid Credentials"
    val NotFound = "Not Found"
  }

  def internalServerErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = ErrorDescriptions.InternalServerError,
    errorDetail = detail
  )

  def badRequestErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = ErrorDescriptions.BadRequest,
    errorDetail = detail
  )

  def forbiddenErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = ErrorDescriptions.Forbidden,
    errorDetail = detail
  )

  def unauthorizedErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = ErrorDescriptions.Unauthorized,
    errorDetail = detail
  )

  def notFoundErrorInfo(detail: Option[String] = None): ErrorInfo = ErrorInfo(
    error = ErrorDescriptions.NotFound,
    errorDetail = detail
  )
}
