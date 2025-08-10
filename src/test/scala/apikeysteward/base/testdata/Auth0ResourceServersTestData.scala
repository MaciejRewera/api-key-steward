package apikeysteward.base.testdata

import apikeysteward.base.testdata.ResourceServersTestData.{resourceServerName_1, resourceServerName_2}
import apikeysteward.connectors.model.auth0.{Auth0ResourceServer, Auth0Scope}

import java.util.UUID

object Auth0ResourceServersTestData {

  val id_1: String = UUID.randomUUID().toString.replaceAll("-", "")
  val id_2: String = UUID.randomUUID().toString.replaceAll("-", "")

  val identifier_1: String = "https://my-domain-1.web-app.example.com"
  val identifier_2: String = "https://my-domain-2.web-app.example.com"

  val scopeRead_1: Auth0Scope  = Auth0Scope(value = "read:scope1", "Read Scope 1")
  val scopeRead_2: Auth0Scope  = Auth0Scope(value = "read:scope2", "Read Scope 2")
  val scopeRead_3: Auth0Scope  = Auth0Scope(value = "read:scope3", "Read Scope 3")
  val scopeWrite_1: Auth0Scope = Auth0Scope(value = "write:scope1", "Write Scope 1")
  val scopeWrite_2: Auth0Scope = Auth0Scope(value = "write:scope2", "Write Scope 2")
  val scopeWrite_3: Auth0Scope = Auth0Scope(value = "write:scope3", "Write Scope 3")

  val auth0ResourceServer_1: Auth0ResourceServer = Auth0ResourceServer(
    id = id_1,
    name = resourceServerName_1,
    is_system = None,
    identifier = identifier_1,
    scopes = Some(List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2))
  )

  val auth0ResourceServer_2: Auth0ResourceServer = Auth0ResourceServer(
    id = id_2,
    name = resourceServerName_2,
    is_system = Some(true),
    identifier = identifier_2,
    scopes = None
  )

}
