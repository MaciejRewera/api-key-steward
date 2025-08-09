package apikeysteward.repositories.db

import apikeysteward.model.errors.Auth0LoginError
import apikeysteward.model.errors.Auth0LoginError.Auth0LoginUpsertError
import apikeysteward.repositories.db.entity.Auth0LoginEntity
import doobie.implicits.{toDoobieApplicativeErrorOps, toSqlInterpolator}
import doobie.postgres._
import doobie.postgres.implicits._

import java.time.{Clock, Instant}

class Auth0LoginDb()(implicit clock: Clock) {

  def upsert(
      entity: Auth0LoginEntity.Write
  ): doobie.ConnectionIO[Either[Auth0LoginError, Auth0LoginEntity.Read]] = {
    val now = Instant.now(clock)
    for {
      eitherResult <- Queries
        .upsert(entity, now)
        .withUniqueGeneratedKeys[Auth0LoginEntity.Read](
          "id",
          "tenant_domain",
          "access_token",
          "scope",
          "expires_in",
          "token_type",
          "created_at",
          "updated_at"
        )
        .attemptSql

      res = eitherResult.left.map(Auth0LoginUpsertError(_))

    } yield res
  }

  def getByTenantDomain(tenantDomain: String): doobie.ConnectionIO[Option[Auth0LoginEntity.Read]] =
    Queries.getByTenantDomain(tenantDomain).option

  private object Queries {

    def upsert(entity: Auth0LoginEntity.Write, now: Instant): doobie.Update0 =
      sql"""INSERT INTO auth0_login_token(id, tenant_domain, access_token, scope, expires_in, token_type, created_at, updated_at)
            VALUES(
              ${entity.id},
              ${entity.tenantDomain},
              ${entity.accessToken},
              ${entity.scope},
              ${entity.expiresIn},
              ${entity.tokenType},
              $now,
              $now
            )
            ON CONFLICT(tenant_domain)
            DO UPDATE SET
              access_token = EXCLUDED.access_token,
              scope = EXCLUDED.scope,
              expires_in = EXCLUDED.expires_in,
              token_type = EXCLUDED.token_type,
              updated_at = EXCLUDED.updated_at
           """.stripMargin.update

    private val columnNamesSelectFragment =
      fr"""SELECT
            auth0_login_token.id,
            auth0_login_token.tenant_domain,
            auth0_login_token.access_token,
            auth0_login_token.scope,
            auth0_login_token.expires_in,
            auth0_login_token.token_type,
            auth0_login_token.created_at,
            auth0_login_token.updated_at
          """

    def getByTenantDomain(tenantDomain: String): doobie.Query0[Auth0LoginEntity.Read] =
      (columnNamesSelectFragment ++
        sql"""FROM auth0_login_token
              WHERE auth0_login_token.tenant_domain = $tenantDomain
             """.stripMargin).query[Auth0LoginEntity.Read]

  }

}
