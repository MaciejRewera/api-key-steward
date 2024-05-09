package apikeysteward.routes

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ErrorInfo {
  val error: String
  val errorDetail: Option[String]
}

object ErrorInfo {

  implicit val errorInfoCodec: Codec[ErrorInfo] = deriveCodec[ErrorInfo]

  case class SimpleErrorInfo(error: String, errorDetail: Option[String]) extends ErrorInfo

  def internalServerErrorInfo(detail: Option[String] = None): ErrorInfo = SimpleErrorInfo(
    error = "Internal Server Error",
    errorDetail = detail
  )

  def badRequestErrorInfo(detail: Option[String] = None): ErrorInfo = SimpleErrorInfo(
    error = "Bad Request",
    errorDetail = detail
  )

  def forbiddenErrorInfo(detail: Option[String] = None): ErrorInfo = SimpleErrorInfo(
    error = "Access Denied",
    errorDetail = detail
  )

  def unauthorizedErrorInfo(detail: Option[String] = None): ErrorInfo = SimpleErrorInfo(
    error = "Invalid Credentials",
    errorDetail = detail
  )

  def notFoundErrorInfo(detail: Option[String] = None): ErrorInfo = SimpleErrorInfo(
    error = "Not Found",
    errorDetail = detail
  )
}
