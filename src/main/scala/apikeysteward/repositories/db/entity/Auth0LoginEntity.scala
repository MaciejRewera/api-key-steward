package apikeysteward.repositories.db.entity

import apikeysteward.connectors.Auth0LoginConnector.Auth0LoginResponse
import apikeysteward.repositories.db.DoobieCustomMeta

import java.time.Instant
import java.util.UUID

object Auth0LoginEntity extends DoobieCustomMeta {

  case class Read(
      id: UUID,
      tenantDomain: String,
      accessToken: String,
      scope: String,
      expiresIn: Int,
      tokenType: String,
      override val createdAt: Instant,
      override val updatedAt: Instant
  ) extends TimestampedEntity

  case class Write(
      id: UUID,
      tenantDomain: String,
      accessToken: String,
      scope: String,
      expiresIn: Int,
      tokenType: String
  )

  object Write {

    def from(id: UUID, tenantDomain: String, auth0LoginResponse: Auth0LoginResponse): Auth0LoginEntity.Write =
      Auth0LoginEntity.Write(
        id = id,
        tenantDomain = tenantDomain,
        accessToken = auth0LoginResponse.access_token,
        scope = auth0LoginResponse.scope,
        expiresIn = auth0LoginResponse.expires_in,
        tokenType = auth0LoginResponse.token_type
      )

  }

}
