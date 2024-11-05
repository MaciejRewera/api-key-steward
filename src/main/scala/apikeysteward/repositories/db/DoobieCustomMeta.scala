package apikeysteward.repositories.db

import doobie.util.meta.Meta

import java.util.UUID
import scala.concurrent.duration.Duration

object DoobieCustomMeta extends DoobieCustomMeta

trait DoobieCustomMeta {

  implicit val uuidMeta: Meta[UUID] =
    Meta[String].timap[UUID](UUID.fromString)(_.toString)

  implicit val DurationMeta: Meta[Duration] =
    Meta[String].timap[Duration](Duration.apply)(_.toString)
}
