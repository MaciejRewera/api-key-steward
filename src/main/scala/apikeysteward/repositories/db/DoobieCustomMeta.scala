package apikeysteward.repositories.db

import doobie.util.meta.Meta

import scala.concurrent.duration.Duration

object DoobieCustomMeta extends DoobieCustomMeta

trait DoobieCustomMeta {

  implicit val DurationMeta: Meta[Duration] =
    Meta[String].timap[Duration](Duration.apply)(_.toString)

}
