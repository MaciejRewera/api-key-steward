package apikeysteward.routes.model

import apikeysteward.routes.model.TapirCustomValidators.ValidateOption
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._

case class CreateApiKeyRequest(
    name: String,
    description: Option[String] = None,
    ttl: Int,
    scopes: List[String]
)

object CreateApiKeyRequest {

  implicit val createApiKeyAdminRequestSchema: Schema[CreateApiKeyRequest] =
    implicitly[Derived[Schema[CreateApiKeyRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(_.validate(Validator.nonEmptyString and Validator.maxLength(250)))
      .modify(_.description)(_.validateOption(Validator.nonEmptyString and Validator.maxLength(250)))
      .modify(_.ttl)(
        _.validate(Validator.positiveOrZero).description("Time-to-live for the API Key. Has to be positive or zero.")
      )

  private def trimStringFields(request: CreateApiKeyRequest): CreateApiKeyRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  implicit val codec: Codec[CreateApiKeyRequest] = deriveCodec[CreateApiKeyRequest]
}
