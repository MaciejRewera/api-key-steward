package apikeysteward.routes

import apikeysteward.model.Tenant
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.TenantRepository
import apikeysteward.routes.definitions.ApiErrorMessages
import cats.data.EitherT
import cats.effect.IO

class ActiveTenantVerifier(tenantRepository: TenantRepository) {

  def verifyTenantIsActive(publicTenantId: TenantId): EitherT[IO, ErrorInfo, Tenant] =
    EitherT {
      for {
        tenantOpt <- tenantRepository.getBy(publicTenantId)
        res = tenantOpt
          .filter(_.isActive)
          .toRight(ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated)))
      } yield res
    }

}
