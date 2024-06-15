package apikeysteward.routes.auth

import apikeysteward.config.JwksConfig
import apikeysteward.routes.auth.UrlJwkProvider.JwksDownloadException
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebKeySet}
import apikeysteward.utils.Logging
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.{Response, Status}
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext.Implicits.global

class UrlJwkProvider(jwksConfig: JwksConfig, httpClient: Client[IO])(implicit runtime: IORuntime)
    extends JwkProvider
    with Logging {

  private val jwksCache: AsyncLoadingCache[Unit, JsonWebKeySet] =
    Scaffeine()
      .recordStats()
      .maximumSize(1)
      .expireAfterWrite(jwksConfig.cacheRefreshPeriod)
      .buildAsyncFuture(_ => fetchJwks().unsafeToFuture())

  private def fetchJwks(): IO[JsonWebKeySet] =
    httpClient.get(jwksConfig.url) {
      case r @ Response(Status.Ok, _, _, _, _) =>
        r.as[JsonWebKeySet]

      case r: Response[IO] =>
        extractErrorResponse(r).flatMap { responseText =>
          logger.warn(s"Call to obtain JWKS from URL: ${jwksConfig.url} failed. Reason: $responseText")
        } >> IO.raiseError(JwksDownloadException(jwksConfig.url.renderString))
    }

  private def extractErrorResponse(response: Response[IO]): IO[String] =
    response.body
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)

  override def getJsonWebKey(keyId: String): IO[Option[JsonWebKey]] =
    IO.fromFuture(IO(jwksCache.get(()).map(_.findBy(keyId))))
}

object UrlJwkProvider {
  case class JwksDownloadException(url: String) extends RuntimeException(s"Call to obtain JWKS from URL: $url failed.")
}
