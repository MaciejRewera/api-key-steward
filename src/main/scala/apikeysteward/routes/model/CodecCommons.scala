package apikeysteward.routes.model

import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.Duration
import scala.util.Try

object CodecCommons extends CodecCommons

trait CodecCommons {
  implicit val finiteDurationCodec: Codec[Duration] =
    Codec
      .from(Decoder.decodeString, Encoder.encodeString)
      .iemapTry(str => Try(Duration(str)))(_.toString)

}
