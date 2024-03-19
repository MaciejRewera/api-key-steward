package apikeysteward.routes

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ErrorInfo {
  val error: String
  val errorDetail: Option[String]
}

object ErrorInfo {

  implicit val errorInfoCodec: Codec[ErrorInfo] = deriveCodec[ErrorInfo]

  case class CommonErrorInfo(error: String, errorDetail: Option[String]) extends ErrorInfo
//  object CommonErrorInfo {
//    implicit val codec: Codec[CommonErrorInfo] = deriveCodec[CommonErrorInfo]
//  }

  def internalServerErrorDetail(detail: Option[String] = None): ErrorInfo = CommonErrorInfo(
    error = "Internal Server Error",
    errorDetail = detail
  )

  def badRequestErrorDetail(detail: Option[String] = None): ErrorInfo = CommonErrorInfo(
    error = "Bad Request",
    errorDetail = detail
  )

  def forbiddenErrorDetail(detail: Option[String] = None): ErrorInfo = CommonErrorInfo(
    error = "Access Denied",
    errorDetail = detail
  )

  def notFoundErrorDetail(detail: Option[String] = None): ErrorInfo = CommonErrorInfo(
    error = "Not Found",
    errorDetail = detail
  )
}
