package apikeysteward.routes

import apikeysteward.routes.definitions.DocumentationEndpoints
import cats.effect.IO
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId, toSemigroupKOps}
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class DocumentationRoutes {

  private val jsonDocsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      DocumentationEndpoints.getJsonDocs
        .serverLogic(_ => Documentation.allJsonDocs.asRight[Unit].pure[IO])
    )

  private val yamlDocsRoute: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      DocumentationEndpoints.getYamlDocs
        .serverLogic(_ => Documentation.allYamlDocs.asRight[Unit].pure[IO])
    )

  val allRoutes: HttpRoutes[IO] = jsonDocsRoute <+> yamlDocsRoute

}
