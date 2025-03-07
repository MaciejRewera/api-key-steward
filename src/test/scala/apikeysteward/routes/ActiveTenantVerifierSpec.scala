package apikeysteward.routes

import apikeysteward.base.testdata.TenantsTestData.{publicTenantId_1, tenant_1}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.repositories.TenantRepository
import apikeysteward.routes.definitions.ApiErrorMessages
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.IdiomaticMockito.StubbingOps
import org.mockito.MockitoSugar._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AsyncWordSpec}

class ActiveTenantVerifierSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with BeforeAndAfterEach {

  private val tenantRepository = mock[TenantRepository]

  private val activeTenantVerifier = new ActiveTenantVerifier(tenantRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(tenantRepository)
  }

  "ActiveTenantVerifier on verifyTenantIsActive" should {

    "call TenantRepository" in {
      tenantRepository.getBy(any[TenantId]) returns IO.pure(Some(tenant_1))

      for {
        _ <- activeTenantVerifier.verifyTenantIsActive(publicTenantId_1).value

        _ = verify(tenantRepository).getBy(eqTo(publicTenantId_1))
      } yield ()
    }

    "return Right containing Tenant when TenantRepository returns active Tenant" in {
      tenantRepository.getBy(any[TenantId]) returns IO.pure(Some(tenant_1))

      activeTenantVerifier
        .verifyTenantIsActive(publicTenantId_1)
        .value
        .asserting(_ shouldBe Right(tenant_1))
    }

    "return Left containing ErrorInfo" when {

      val expectedErrorInfo = ErrorInfo.badRequestErrorInfo(Some(ApiErrorMessages.General.TenantIsDeactivated))

      "TenantRepository returns empty Option" in {
        tenantRepository.getBy(any[TenantId]) returns IO.pure(None)

        activeTenantVerifier
          .verifyTenantIsActive(publicTenantId_1)
          .value
          .asserting(_ shouldBe Left(expectedErrorInfo))
      }

      "TenantRepository returns deactivated Tenant" in {
        val deactivatedTenant = tenant_1.copy(isActive = false)
        tenantRepository.getBy(any[TenantId]) returns IO.pure(Some(deactivatedTenant))

        activeTenantVerifier
          .verifyTenantIsActive(publicTenantId_1)
          .value
          .asserting(_ shouldBe Left(expectedErrorInfo))
      }
    }
  }

}
