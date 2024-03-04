package apikeysteward.repositories.db.entity

import java.time.Instant

object ClientUsersEntity {

  case class Read(
      id: Long,
      clientId: String,
      userId: String,
      createdAt: Instant,
      updatedAt: Instant
  )

  case class Write(
      clientId: String,
      userId: String
  )
}
