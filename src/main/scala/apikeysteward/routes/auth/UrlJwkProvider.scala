package apikeysteward.routes.auth

import apikeysteward.config.JwksConfig
import apikeysteward.routes.auth.UrlJwkProvider.JwksDownloadException
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebKeySet}
import apikeysteward.utils.Logging
import cats.Monoid
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.toTraverseOps
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import fs2.Stream
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.{Response, Status, Uri}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class UrlJwkProvider(jwksConfig: JwksConfig, httpClient: Client[IO])(implicit runtime: IORuntime)
    extends JwkProvider
    with Logging {

  private val jwksCache: AsyncLoadingCache[Unit, JsonWebKeySet] =
    Scaffeine()
      .recordStats()
      .maximumSize(1)
      .expireAfterWrite(jwksConfig.cacheRefreshPeriod)
      .buildAsyncFuture(_ => fetchAllJwkSets().unsafeToFuture())

  private def fetchAllJwkSets()(implicit M: Monoid[JsonWebKeySet]): IO[JsonWebKeySet] =
    Stream
      .emits(jwksConfig.urls)
      .covary[IO]
      .parEvalMap(jwksConfig.urls.length)(fetchSingleJwkSetOrEmpty)
      .compile
      .toList
      .map(M.combineAll)

  private def fetchSingleJwkSetOrEmpty(url: Uri): IO[JsonWebKeySet] =
    fetchSingleJwkSetWithRetry(url).recover { case _: JwksDownloadException => JsonWebKeySet.empty }

  private def fetchSingleJwkSetWithRetry(url: Uri)(implicit M: Monoid[JsonWebKeySet]): IO[JsonWebKeySet] =
    Stream
      .retry(
        fo = fetchSingleJwkSet(url),
        delay = jwksConfig.fetchRetryAttemptInitialDelay,
        nextDelay = _ * 2,
        maxAttempts = jwksConfig.fetchRetryAttemptMaxAmount,
        retriable = _.isInstanceOf[JwksDownloadException]
      )
      .compile
      .toList
      .map(M.combineAll)

  private def fetchSingleJwkSet(url: Uri): IO[JsonWebKeySet] =
    httpClient.get(url) {
      case r @ Response(Status.Ok, _, _, _, _) =>
        r.as[JsonWebKeySet]

      case r: Response[IO] =>
        extractErrorResponse(r).flatMap { responseText =>
          logger.warn(s"Call to obtain JWKS from URL: $url failed. Reason: $responseText")
        } >> IO.raiseError(JwksDownloadException(url.renderString))
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
