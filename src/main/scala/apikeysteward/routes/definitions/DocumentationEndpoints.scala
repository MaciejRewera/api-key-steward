package apikeysteward.routes.definitions

import sttp.tapir._

object DocumentationEndpoints {

  val getJsonDocs: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("docs" / "v0" / "docs.json").out(stringBody)

  val getYamlDocs: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("docs" / "v0" / "docs.yaml").out(stringBody)
}
