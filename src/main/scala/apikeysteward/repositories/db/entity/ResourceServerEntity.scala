package apikeysteward.repositories.db.entity

import apikeysteward.model.{ResourceServer, ResourceServerUpdate}

import java.time.Instant
import java.util.UUID

object ResourceServerEntity {

  case class Read(
      id: UUID,
      tenantId: UUID,
      publicResourceServerId: String,
      name: String,
      description: Option[String],
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      tenantId: UUID,
      publicResourceServerId: String,
      name: String,
      description: Option[String]
  )

  object Write {
    def from(id: UUID, tenantId: UUID, resourceServer: ResourceServer): ResourceServerEntity.Write =
      ResourceServerEntity.Write(
        id = id,
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
