package apikeysteward.connectors

import cats.effect.IO
import org.http4s.Response

object ConnectorUtils {

  def extractErrorResponse(response: Response[IO]): IO[String] =
    response.body
      .through(fs2.text.utf8.decode)
      .compile
      .toList
      .map(_.mkString)

}
