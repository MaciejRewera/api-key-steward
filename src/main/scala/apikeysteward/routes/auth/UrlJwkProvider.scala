package apikeysteward.routes.auth

import apikeysteward.config.JwksConfig
import apikeysteward.routes.auth.UrlJwkProvider.JwksDownloadException
import apikeysteward.routes.auth.model.{JsonWebKey, JsonWebKeySet}
import apikeysteward.utils.Logging
import cats.Monoid
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.github.blemale.scaffeine.{AsyncCache, Scaffeine}
import fs2.Stream
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.{Response, Status, Uri}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UrlJwkProvider(jwksConfig: JwksConfig, httpClient: Client[IO])(implicit runtime: IORuntime)
    extends JwkProvider
    with Logging {

  private val allUrlsAmount = jwksConfig.urls.length

  private val jwksUrlsFetchCache: AsyncCache[Uri, JsonWebKeySet] =
    Scaffeine()
      .recordStats()
      .maximumSize(allUrlsAmount)
      .expireAfterWrite(jwksConfig.cacheRefreshPeriod)
      .buildAsync()

  override def getJsonWebKey(keyId: String): IO[Option[JsonWebKey]] =
    IO.fromFuture(IO {
      jwksUrlsFetchCache
        .getAllFuture(jwksConfig.urls, loadCache)
        .map(combineSets)
    }).map(_.findBy(keyId))

  private def loadCache(urls: Iterable[Uri]): Future[Map[Uri, JsonWebKeySet]] =
    fetchJwkSets(urls.toSeq).unsafeToFuture()

  private def fetchJwkSets(urls: Seq[Uri]): IO[Map[Uri, JsonWebKeySet]] =
    Stream
      .evalSeq(IO.pure(urls))
      .parEvalMap(urls.length) { url =>
        for {
          jwksOpt <- fetchSingleJwkSetOrNone(url)
          result = jwksOpt.map(url -> _)
        } yield result
      }
      .collect { case Some(tuple) => tuple }
      .compile
      .to(Map)

  private def fetchSingleJwkSetOrNone(url: Uri): IO[Option[JsonWebKeySet]] =
    fetchSingleJwkSetWithRetry(url)
      .map(Some(_))
      .recover { case _: JwksDownloadException => None }

  private def fetchSingleJwkSetWithRetry(url: Uri)(implicit M: Monoid[JsonWebKeySet]): IO[JsonWebKeySet] =
    Stream
      .retry(
        fo = fetchSingleJwkSet(url),
        delay = jwksConfig.fetchRetryAttemptInitialDelay,
        nextDelay = _ * 2,
        maxAttempts = jwksConfig.fetchRetryMaxAttempts,
        retriable = _.isInstanceOf[JwksDownloadException]
      )
      .compile
      .foldMonoid

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

  private def combineSets(setsMap: Map[Uri, JsonWebKeySet])(implicit M: Monoid[JsonWebKeySet]): JsonWebKeySet =
    M.combineAll(setsMap.values)
}

object UrlJwkProvider {
  case class JwksDownloadException(url: String) extends RuntimeException(s"Call to obtain JWKS from URL: $url failed.")
}
