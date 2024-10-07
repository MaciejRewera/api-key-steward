package apikeysteward.repositories.db.entity

import java.time.Instant

object ApplicationEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      publicApplicationId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant,
      deactivatedAt: Option[Instant]
  ) extends TimestampedEntity

  case class Write(
      tenantId: Long,
      publicApplicationId: String,
      name: String,
      description: Option[String]
  )

  object Write {}

  case class Update(
      publicApplicationId: String,
      name: String,
      description: Option[String]
  )

  object Update {}

}
