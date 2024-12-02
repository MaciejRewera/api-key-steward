package apikeysteward.repositories.db

import apikeysteward.model.Tenant.TenantId
import doobie.implicits.toSqlInterpolator
import doobie.util.fragment

private[db] trait TenantIdScopedQueriesBase {
  val publicTenantId: TenantId

  val tenantIdFr: fragment.Fragment =
    fr"""tenant_id = (
        |  SELECT tenant.id
        |  FROM tenant
        |  WHERE tenant.public_tenant_id = ${publicTenantId.toString}
        |)
            """.stripMargin
}
