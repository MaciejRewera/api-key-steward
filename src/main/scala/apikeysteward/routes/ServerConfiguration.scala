package apikeysteward.routes

import cats.effect.IO
import sttp.tapir.server.http4s.Http4sServerOptions

object ServerConfiguration {

  val options: Http4sServerOptions[IO] = Http4sServerOptions
    .customiseInterceptors[IO]
    .serverLog(Http4sServerOptions.defaultServerLog[IO])
    .options
}
