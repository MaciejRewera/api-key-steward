package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.util.Try

case class ApiKeyTemplate(
    publicTemplateId: ApiKeyTemplateId,
    isDefault: Boolean,
    name: String,
    description: Option[String],
    apiKeyMaxExpiryPeriod: Duration
)

object ApiKeyTemplate {
  implicit private val finiteDurationCodec: Codec[Duration] =
    Codec
      .from(Decoder.decodeString, Encoder.encodeString)
      .iemapTry(str => Try(Duration(str)))(_.toString)

  implicit val codec: Codec[ApiKeyTemplate] = deriveCodec[ApiKeyTemplate]

  type ApiKeyTemplateId = UUID
}
