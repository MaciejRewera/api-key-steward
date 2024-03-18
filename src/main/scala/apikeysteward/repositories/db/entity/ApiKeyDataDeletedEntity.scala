package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyDataDeletedEntity {

  case class Read(
      id: Long,
      deletedAt: Instant,
      publicKeyId: String,
      name: String,
      description: Option[String] = None,
      userId: String,
      expiresAt: Instant,
      createdAt: Instant,
      updatedAt: Instant
  )
}
