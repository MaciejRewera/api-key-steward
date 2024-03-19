package apikeysteward.routes

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.EncoderOps
import sttp.model.StatusCode
import sttp.tapir.DecodeResult.InvalidValue
import sttp.tapir.json.circe.{jsonBody, _}
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.{FailureMessages, ValidationMessages}
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.statusCode
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}

object ServerConfiguration {

  private val customDecodeFailureHandler: DecodeFailureHandler = context =>
    DefaultDecodeFailureHandler
      .respond(
        ctx = context,
        badRequestOnPathErrorIfPathShapeMatches = false,
        badRequestOnPathInvalidIfPathShapeMatches = true
      )
      .map { case (status, _) =>
        val responseBody = context.failure match {
          case _ if status == StatusCode.Forbidden =>
            ErrorInfo.forbiddenErrorDetail(Some(FailureMessages.failureMessage(context)))
          case _ if status == StatusCode.NotFound =>
            ErrorInfo.notFoundErrorDetail(Some(FailureMessages.failureMessage(context)))
          case _ if status == StatusCode.UnsupportedMediaType =>
            ErrorInfo.badRequestErrorDetail(Some("Unsupported content type"))

          case _ => ErrorInfo.badRequestErrorDetail(Some(FailureMessages.failureMessage(context)))
        }

        ValuedEndpointOutput(statusCode.and(jsonBody[Json]), status -> responseBody.asJson)
      }

  private val customExceptionHandler: ExceptionHandler[IO] = ExceptionHandler.pure { (_: ExceptionContext) =>
    Some(
      ValuedEndpointOutput(
        output = statusCode.and(jsonBody[Json]),
        value = StatusCode.InternalServerError -> ErrorInfo.internalServerErrorDetail().asJson
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
