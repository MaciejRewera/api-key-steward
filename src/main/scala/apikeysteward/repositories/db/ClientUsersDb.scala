package apikeysteward.repositories.db

import apikeysteward.repositories.db.entity.ClientUsersEntity
import doobie.implicits.toSqlInterpolator
import fs2.Stream

import java.time.{Clock, Instant}

class ClientUsersDb()(implicit clock: Clock) {

  import doobie.postgres._
  import doobie.postgres.implicits._

  def insert(clientUsersEntity: ClientUsersEntity.Write): doobie.ConnectionIO[ClientUsersEntity.Read] = {
    val now = Instant.now(clock)
    Queries
      .insert(clientUsersEntity, now)
      .withUniqueGeneratedKeys[ClientUsersEntity.Read](
        "id",
        "client_id",
        "user_id",
        "created_at",
        "updated_at"
      )
  }

  def getAllByClientId(clientId: String): Stream[doobie.ConnectionIO, ClientUsersEntity.Read] =
    Queries.getAllByClientId(clientId).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, client_id, user_id, created_at, updated_at"

    def insert(clientUsersEntity: ClientUsersEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO client_users(client_id, user_id, created_at, updated_at) VALUES (
           |  ${clientUsersEntity.clientId},
           |  ${clientUsersEntity.userId},
           |  $now,
           |  $now
           |)
           |""".stripMargin.update

    def getAllByClientId(clientId: String): doobie.Query0[ClientUsersEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM client_users WHERE client_id = $clientId").query[ClientUsersEntity.Read]

  }
}
