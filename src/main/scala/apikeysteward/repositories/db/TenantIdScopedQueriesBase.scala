package apikeysteward.repositories.db

import apikeysteward.model.Tenant.TenantId
import doobie.Fragment
import doobie.implicits.toSqlInterpolator
import doobie.util.fragment

private[db] trait TenantIdScopedQueriesBase {

  val publicTenantId: TenantId

  def tenantIdFr(tableName: String): fragment.Fragment = {
    val fullColumnName: Fragment = Fragment.const(s"$tableName.tenant_id")

    fullColumnName ++
      fr""" = (
          |  SELECT tenant.id
          |  FROM tenant
          |  WHERE tenant.public_tenant_id = ${publicTenantId.toString}
          |)""".stripMargin
  }
}
