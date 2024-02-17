package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyEntity {

  case class Read(
    id: Long,
    createdAt: Instant,
    updatedAt: Instant
  )

  case class Write(
    apiKey: String
  )
}
