package apikeysteward.repositories.db.entity
import java.time.Instant
import scala.concurrent.duration.Duration

object ApiKeyTemplateEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      publicTemplateId: String,
      isDefault: Boolean,
      name: String,
      description: Option[String],
      apiKeyMaxExpiryPeriod: Duration,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      tenantId: Long,
      publicTemplateId: String,
      isDefault: Boolean,
      name: String,
      description: Option[String],
      apiKeyMaxExpiryPeriod: Duration
  )

  object Write {}

  case class Update(
      publicTemplateId: String,
      isDefault: Boolean,
      name: String,
      description: Option[String],
      apiKeyMaxExpiryPeriod: Duration
  )

  object Update {}

}
