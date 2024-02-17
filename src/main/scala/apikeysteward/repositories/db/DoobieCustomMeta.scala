package apikeysteward.repositories.db

import doobie.util.meta.Meta
import io.circe.Json

import java.time.{Instant, LocalDate, LocalDateTime, ZonedDateTime}
import java.util.UUID

object DoobieCustomMeta extends DoobieCustomMeta

trait DoobieCustomMeta {

  import io.circe.parser._

  implicit val uuidMeta: Meta[UUID] =
    Meta[String].timap[UUID](UUID.fromString)(_.toString)

  implicit val JavaTimeLocalDateMeta: Meta[ZonedDateTime] =
    doobie.implicits.javatimedrivernative.JavaZonedDateTimeMeta

  implicit val JavaTimeLocalDateTimeMeta: Meta[Instant] =
    doobie.implicits.javatimedrivernative.JavaTimeInstantMeta
}
