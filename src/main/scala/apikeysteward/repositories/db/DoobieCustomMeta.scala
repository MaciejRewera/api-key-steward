package apikeysteward.repositories.db

import doobie.util.meta.Meta

import java.time.{Instant, ZonedDateTime}
import java.util.UUID

object DoobieCustomMeta extends DoobieCustomMeta

trait DoobieCustomMeta {

  implicit val uuidMeta: Meta[UUID] =
    Meta[String].timap[UUID](UUID.fromString)(_.toString)

  implicit val JavaTimeLocalDateMeta: Meta[ZonedDateTime] =
    doobie.implicits.javatimedrivernative.JavaZonedDateTimeMeta

  implicit val JavaTimeLocalDateTimeMeta: Meta[Instant] =
    doobie.implicits.javatimedrivernative.JavaTimeInstantMeta
}
