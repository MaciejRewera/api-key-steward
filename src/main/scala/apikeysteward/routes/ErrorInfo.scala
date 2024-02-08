package apikeysteward.routes

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ErrorInfo {
  val error: String
  val errorDetail: Option[String]
}

object ErrorInfo {

  case class CommonErrorInfo(error: String, errorDetail: Option[String]) extends ErrorInfo
  object CommonErrorInfo {
    implicit val codec: Codec[CommonErrorInfo] = deriveCodec[CommonErrorInfo]
  }

  def forbiddenErrorDetail(detail: Option[String] = None): ErrorInfo = CommonErrorInfo(
    error = "Access denied",
    errorDetail = detail
  )
}
