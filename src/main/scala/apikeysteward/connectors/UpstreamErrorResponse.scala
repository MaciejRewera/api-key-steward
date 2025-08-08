package apikeysteward.connectors

import apikeysteward.model.errors.CustomError

trait UpstreamErrorResponse extends CustomError {
  def statusCode: Int
}

object UpstreamErrorResponse {

  def apply(statusCode: Int, message: String): UpstreamErrorResponse =
    UpstreamErrorResponseImpl(statusCode, message)

  private case class UpstreamErrorResponseImpl(override val statusCode: Int, override val message: String)
      extends UpstreamErrorResponse

  object Upstream4xxErrorResponseException {

    def unapply(e: UpstreamErrorResponse): Option[UpstreamErrorResponse] =
      if (e.statusCode >= 400 && e.statusCode < 500) Some(e) else None

  }

  object Upstream5xxErrorResponseException {

    def unapply(e: UpstreamErrorResponse): Option[UpstreamErrorResponse] =
      if (e.statusCode >= 500 && e.statusCode < 600) Some(e) else None

  }

}
