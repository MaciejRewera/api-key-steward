package apikeysteward.routes.model

import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.Duration
import scala.util.Try

object CodecCommons extends CodecCommons

trait CodecCommons {

  implicit val finiteDurationCodec: Codec[Duration] = {
    val AllowedTimeUnits: List[String] = List(
      Seq("d", "day", "days"),
      Seq("h", "hr", "hrs", "hour", "hours"),
      Seq("m", "min", "mins", "minute", "minutes"),
      Seq("s", "sec", "secs", "second", "seconds"),
      Seq("ms", "milli", "millis", "millisecond", "milliseconds"),
      Seq("µs", "micro", "micros", "microsecond", "microseconds"),
      Seq("ns", "nano", "nanos", "nanosecond", "nanoseconds"),
      Seq("Inf", "PlusInf", "+Inf", "Duration.Inf")
    ).flatten

    val AllowedTimeUnitsFormatted: String = AllowedTimeUnits.mkString("[", ", ", "]")

    val errorMessage =
      s"""Incorrect Duration format. The correct format is "<length><unit>", where whitespace is allowed before, between and after the parts. Allowed time units: $AllowedTimeUnitsFormatted"""

    Codec
      .from(Decoder.decodeString, Encoder.encodeString)
      .iemap(str => Try(Duration(str)).toEither.left.map(_ => errorMessage))(_.toString)
  }

}
