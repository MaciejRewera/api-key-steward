package apikeysteward.repositories.db

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.PermissionInsertionError._
import apikeysteward.model.RepositoryErrors.PermissionDbError._
import apikeysteward.repositories.db.PermissionDb.ColumnNamesSelectFragment
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
      publicApplicationId: ApplicationId,
      publicPermissionId: PermissionId
  ): doobie.ConnectionIO[Either[PermissionNotFoundError, PermissionEntity.Read]] =
    for {
      permissionToDeleteE <- getBy(publicApplicationId, publicPermissionId).map(
        _.toRight(PermissionNotFoundError(publicApplicationId, publicPermissionId))
      )
      resultE <- permissionToDeleteE.traverse { result =>
        Queries.delete(publicApplicationId, publicPermissionId).run.map(_ => result)
      }
    } yield resultE

  def getBy(
      publicApplicationId: ApplicationId,
      publicPermissionId: PermissionId
  ): doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Queries.getBy(publicApplicationId, publicPermissionId).option

  def getByPublicPermissionId(publicPermissionId: PermissionId): doobie.ConnectionIO[Option[PermissionEntity.Read]] =
    Queries.getByPublicPermissionId(publicPermissionId).option

  def getAllBy(publicApplicationId: ApplicationId)(
      nameFragment: Option[String]
  ): Stream[doobie.ConnectionIO, PermissionEntity.Read] =
    Queries.getAllBy(publicApplicationId)(nameFragment).stream

  private object Queries {

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

    def delete(publicApplicationId: ApplicationId, publicPermissionId: PermissionId): doobie.Update0 =
      sql"""DELETE FROM permission
            USING application
            WHERE application.public_application_id = ${publicApplicationId.toString}
              AND permission.public_permission_id = ${publicPermissionId.toString}
           """.stripMargin.update

    def getBy(
        publicApplicationId: ApplicationId,
        publicPermissionId: PermissionId
    ): doobie.Query0[PermissionEntity.Read] =
      (ColumnNamesSelectFragment ++
        sql"""FROM permission
              JOIN application ON application.id = permission.application_id
              WHERE application.public_application_id = ${publicApplicationId.toString}
                AND permission.public_permission_id = ${publicPermissionId.toString}
             """).query[PermissionEntity.Read]

    def getByPublicPermissionId(publicPermissionId: PermissionId): doobie.Query0[PermissionEntity.Read] =
      (ColumnNamesSelectFragment ++
        sql"""FROM permission
              JOIN application ON application.id = permission.application_id
              WHERE permission.public_permission_id = ${publicPermissionId.toString}
             """).query[PermissionEntity.Read]

    def getAllBy(
        publicApplicationId: ApplicationId
    )(nameFragment: Option[String]): doobie.Query0[PermissionEntity.Read] = {
      val publicApplicationIdEquals = Some(fr"application.public_application_id = ${publicApplicationId.toString}")
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

private[db] object PermissionDb {

  val ColumnNamesSelectFragment =
    fr"""SELECT
            permission.id,
            permission.application_id,
            permission.public_permission_id,
            permission.name,
            permission.description,
            permission.created_at,
            permission.updated_at
          """
}
