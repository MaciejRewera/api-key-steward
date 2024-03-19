package apikeysteward.routes.model.admin

import apikeysteward.routes.TapirCustomValidators.ValidateOption
import sttp.tapir._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.generic.auto._
import sttp.tapir.generic.Derived

case class CreateApiKeyAdminRequest(
    name: String,
    description: Option[String] = None,
    ttl: Int
)

object CreateApiKeyAdminRequest {

  implicit val createApiKeyAdminRequestSchema: Schema[CreateApiKeyAdminRequest] =
    implicitly[Derived[Schema[CreateApiKeyAdminRequest]]].value
      .modify(_.name)(_.validate(Validator.nonEmptyString and Validator.maxLength(250)))
      .modify(_.description)(_.validateOption(Validator.nonEmptyString and Validator.maxLength(250)))
      .modify(_.ttl)(
        _.validate(Validator.positiveOrZero).description("Time to live. Has to be positive or zero.")
      )

  implicit val codec: Codec[CreateApiKeyAdminRequest] = deriveCodec[CreateApiKeyAdminRequest]

}
