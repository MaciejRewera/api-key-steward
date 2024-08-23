package apikeysteward.repositories.db.entity

import java.time.Instant

object ApiKeyTemplateEntity {

  case class Read(
      id: Long,
      publicId: String,
      apiKeyExpiryPeriodMaxSeconds: Int,
      createdAt: Instant,
      updatedAt: Instant
  )

  case class Write(
      publicId: String,
      apiKeyExpiryPeriodMaxSeconds: Int
  )

}
