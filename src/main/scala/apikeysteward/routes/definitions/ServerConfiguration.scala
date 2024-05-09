package apikeysteward.routes.definitions

import apikeysteward.routes.ErrorInfo
import cats.effect.IO
import io.circe.Json
import io.circe.syntax.EncoderOps
import sttp.model.StatusCode
import sttp.tapir.json.circe.{jsonBody, _}
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.FailureMessages
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.statusCode

object ServerConfiguration {

  private val customDecodeFailureHandler: DecodeFailureHandler = context =>
    DefaultDecodeFailureHandler
      .respond(
        ctx = context,
        badRequestOnPathErrorIfPathShapeMatches = true,
        badRequestOnPathInvalidIfPathShapeMatches = true
      )
      .map { case (status, _) =>
        val responseBody = context.failure match {

          case _ if status == StatusCode.Unauthorized =>
            ErrorInfo.unauthorizedErrorInfo(Some(FailureMessages.failureMessage(context)))

          case _ if status == StatusCode.Forbidden =>
            ErrorInfo.forbiddenErrorInfo(Some(FailureMessages.failureMessage(context)))

          case _ if status == StatusCode.NotFound =>
            ErrorInfo.notFoundErrorInfo(Some(FailureMessages.failureMessage(context)))

          case _ if status == StatusCode.UnsupportedMediaType =>
            ErrorInfo.badRequestErrorInfo(Some("Unsupported content type"))

          case _ => ErrorInfo.badRequestErrorInfo(Some(FailureMessages.failureMessage(context)))
        }

        ValuedEndpointOutput(statusCode.and(jsonBody[Json]), status -> responseBody.asJson)
      }

  private val customExceptionHandler: ExceptionHandler[IO] = ExceptionHandler.pure { (_: ExceptionContext) =>
    Some(
      ValuedEndpointOutput(
        output = statusCode.and(jsonBody[Json]),
        value = StatusCode.InternalServerError -> ErrorInfo.internalServerErrorInfo().asJson
      )
    )
  }

  val options: Http4sServerOptions[IO] = Http4sServerOptions
    .customiseInterceptors[IO]
    .decodeFailureHandler(customDecodeFailureHandler)
    .exceptionHandler(customExceptionHandler)
    .serverLog(Http4sServerOptions.defaultServerLog[IO])
    .options
}
