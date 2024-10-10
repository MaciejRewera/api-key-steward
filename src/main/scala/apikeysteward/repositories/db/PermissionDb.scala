package apikeysteward.repositories.db

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError._
import apikeysteward.repositories.db.entity.PermissionEntity
import cats.implicits.toTraverseOps
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.sqlstate.class23.{FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION}
import doobie.util.fragments.whereAndOpt
import fs2.Stream

import java.sql.SQLException
import java.time.{Clock, Instant}

class PermissionDb()(implicit clock: Clock) {

  def insert(
      permissionEntity: PermissionEntity.Write
  ): doobie.ConnectionIO[Either[PermissionInsertionError, PermissionEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .insert(permissionEntity, now)
        .withUniqueGeneratedKeys[PermissionEntity.Read](
          "id",
          "application_id",
          "public_permission_id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(recoverSqlException(_, permissionEntity))

    } yield res
  }

  private def recoverSqlException(
      sqlException: SQLException,
      permissionEntity: PermissionEntity.Write
  ): PermissionInsertionError =
    sqlException.getSQLState match {
      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("public_permission_id") =>
        PermissionAlreadyExistsError(permissionEntity.publicPermissionId)

      case UNIQUE_VIOLATION.value if sqlException.getMessage.contains("application_id, name") =>
        PermissionAlreadyExistsForThisApplicationError(permissionEntity.name, permissionEntity.applicationId)

      case FOREIGN_KEY_VIOLATION.value => ReferencedApplicationDoesNotExistError(permissionEntity.applicationId)

      case _ => PermissionInsertionErrorImpl(sqlException)
    }

  def delete(
      publicPermissionId: PermissionId
  ): doobie.ConnectionIO[Either[PermissionNotFoundError, PermissionEntity.Read]] =
    for {
      permissionToDeleteE <- getByPublicPermissionId(publicPermissionId).map(
        _.toRight(PermissionNotFoundError(publicPermissionId.toString))
      )
      resultE <- permissionToDeleteE.traverse { result =>
        Queries.delete(publicPermissionId.toString).run.map(_ => result)
      }
    } yield resultE

  def getByPublicPermissionId(publicPermissionId: PermissionId): doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Queries.getBy(publicPermissionId.toString).option

  def getAllBy(publicApplicationId: ApplicationId)(
      nameFragment: Option[String]
  ): Stream[doobie.ConnectionIO, PermissionEntity.Read] =
    Queries.getAllBy(publicApplicationId.toString)(nameFragment).stream

  private object Queries {

    private val columnNamesSelectFragment =
      fr"SELECT id, application_id, public_permission_id, name, description, created_at, updated_at"

    def insert(permissionEntity: PermissionEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO permission(application_id, public_permission_id, name, description, created_at, updated_at)
            VALUES(
              ${permissionEntity.applicationId},
              ${permissionEntity.publicPermissionId},
              ${permissionEntity.name},
              ${permissionEntity.description},
              $now,
              $now
            )
           """.stripMargin.update

    def delete(publicPermissionId: String): doobie.Update0 =
      sql"""DELETE FROM permission
            WHERE permission.public_permission_id = $publicPermissionId
           """.stripMargin.update

    def getBy(publicPermissionId: String): doobie.Query0[PermissionEntity.Read] =
      (columnNamesSelectFragment ++
        sql"FROM permission WHERE permission.public_permission_id = $publicPermissionId").query[PermissionEntity.Read]

    def getAllBy(publicApplicationId: String)(nameFragment: Option[String]): doobie.Query0[PermissionEntity.Read] = {
      val publicApplicationIdEquals = Some(fr"application.public_application_id = $publicApplicationId")
      val nameLikeFr = nameFragment
        .map(n => s"%$n%")
        .map(n => fr"permission.name ILIKE $n")

      sql"""SELECT
              permission.id,
              permission.application_id,
              permission.public_permission_id,
              permission.name,
              permission.description,
              permission.created_at,
              permission.updated_at
            FROM permission
            JOIN application ON application.id = permission.application_id
            ${whereAndOpt(publicApplicationIdEquals, nameLikeFr)}
            ORDER BY permission.name
            """.stripMargin.query[PermissionEntity.Read]
    }

  }
}
