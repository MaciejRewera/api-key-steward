package apikeysteward.repositories.db.entity

import java.time.Instant

trait TimestampedEntity {
  val createdAt: Instant
  val updatedAt: Instant
}
