package apikeysteward.base.testdata

import apikeysteward.base.FixedClock
import apikeysteward.connectors.Auth0LoginConnector.Auth0LoginResponse
import apikeysteward.repositories.db.entity.Auth0LoginEntity

import java.util.UUID

object Auth0LoginTestData extends FixedClock {

  val auth0LoginDbId_1: UUID = UUID.randomUUID()
  val auth0LoginDbId_2: UUID = UUID.randomUUID()

  val tenantDomain_1 = "https://test.audience.com/api/v1"
  val tenantDomain_2 = "https://test.audience.com/api/v2"

  val accessToken_1 = "test-access-token-001"
  val accessToken_2 = "test-access-token-002"

  val scope_1 = "read:scope1 write:scope1 read:scope2 read:scope3"
  val scope_2 = "read:scope2 write:scope2 read:scope3 read:scope4"

  val expiresIn_1 = 86400
  val expiresIn_2 = 3600

  val tokenType_1 = "Bearer-1"
  val tokenType_2 = "Bearer-2"

  val auth0LoginEntityWrite_1: Auth0LoginEntity.Write = Auth0LoginEntity.Write(
    id = auth0LoginDbId_1,
    tenantDomain = tenantDomain_1,
    accessToken = accessToken_1,
    scope = scope_1,
    expiresIn = expiresIn_1,
    tokenType = tokenType_1
  )

  val auth0LoginEntityRead_1: Auth0LoginEntity.Read = Auth0LoginEntity.Read(
    id = auth0LoginDbId_1,
    tenantDomain = tenantDomain_1,
    accessToken = accessToken_1,
    scope = scope_1,
    expiresIn = expiresIn_1,
    tokenType = tokenType_1,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val auth0LoginEntityWrite_2: Auth0LoginEntity.Write = Auth0LoginEntity.Write(
    id = auth0LoginDbId_2,
    tenantDomain = tenantDomain_2,
    accessToken = accessToken_2,
    scope = scope_2,
    expiresIn = expiresIn_2,
    tokenType = tokenType_2
  )

  val auth0LoginEntityRead_2: Auth0LoginEntity.Read = Auth0LoginEntity.Read(
    id = auth0LoginDbId_2,
    tenantDomain = tenantDomain_2,
    accessToken = accessToken_2,
    scope = scope_2,
    expiresIn = expiresIn_2,
    tokenType = tokenType_2,
    createdAt = nowInstant,
    updatedAt = nowInstant
  )

  val auth0LoginResponse_1: Auth0LoginResponse = Auth0LoginResponse(
    access_token = accessToken_1,
    scope = scope_1,
    expires_in = expiresIn_1,
    token_type = tokenType_1
  )

}
