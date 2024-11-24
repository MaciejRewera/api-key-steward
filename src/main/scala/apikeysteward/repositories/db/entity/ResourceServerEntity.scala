package apikeysteward.repositories.db.entity

import apikeysteward.model.{ResourceServer, ResourceServerUpdate}

import java.time.Instant

object ResourceServerEntity {

  case class Read(
      id: Long,
      tenantId: Long,
      publicResourceServerId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant,
      deactivatedAt: Option[Instant]
  ) extends TimestampedEntity

  case class Write(
      tenantId: Long,
      publicResourceServerId: String,
      name: String,
      description: Option[String]
  )

  object Write {
    def from(tenantId: Long, resourceServer: ResourceServer): ResourceServerEntity.Write =
      ResourceServerEntity.Write(
        tenantId = tenantId,
        publicResourceServerId = resourceServer.resourceServerId.toString,
        name = resourceServer.name,
        description = resourceServer.description
      )
  }

  case class Update(
      publicResourceServerId: String,
      name: String,
      description: Option[String]
  )

  object Update {
    def from(resourceServerUpdate: ResourceServerUpdate): ResourceServerEntity.Update =
      ResourceServerEntity.Update(
        publicResourceServerId = resourceServerUpdate.resourceServerId.toString,
        name = resourceServerUpdate.name,
        description = resourceServerUpdate.description
      )
  }

}
