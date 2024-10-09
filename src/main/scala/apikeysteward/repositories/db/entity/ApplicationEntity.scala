package apikeysteward.repositories.db.entity

import apikeysteward.model.{Application, ApplicationUpdate}

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

  object Write {
    def from(tenantId: Long, application: Application): ApplicationEntity.Write =
      ApplicationEntity.Write(
        tenantId = tenantId,
        publicApplicationId = application.applicationId.toString,
        name = application.name,
        description = application.description
      )
  }

  case class Update(
      publicApplicationId: String,
      name: String,
      description: Option[String]
  )

  object Update {
    def from(applicationUpdate: ApplicationUpdate): ApplicationEntity.Update =
      ApplicationEntity.Update(
        publicApplicationId = applicationUpdate.applicationId.toString,
        name = applicationUpdate.name,
        description = applicationUpdate.description
      )
  }

}
