package apikeysteward.model

import java.util.UUID

object RepositoryErrors {

  sealed abstract class ApiKeyDbError(override val message: String) extends CustomError
  object ApiKeyDbError {

    sealed abstract class ApiKeyInsertionError(override val message: String) extends ApiKeyDbError(message)

    object ApiKeyInsertionError {

      case object ApiKeyAlreadyExistsError extends ApiKeyInsertionError(message = "API Key already exists.")

      case object ApiKeyIdAlreadyExistsError
          extends ApiKeyInsertionError(message = "API Key Data with the same apiKeyId already exists.")

      case object PublicKeyIdAlreadyExistsError
          extends ApiKeyInsertionError(message = "API Key Data with the same publicKeyId already exists.")
    }

    def apiKeyDataNotFoundError(userId: String, publicKeyId: UUID): ApiKeyDbError =
      apiKeyDataNotFoundError(userId, publicKeyId.toString)

    def apiKeyDataNotFoundError(userId: String, publicKeyId: String): ApiKeyDbError =
      ApiKeyDataNotFoundError(userId, publicKeyId)

    case class ApiKeyDataNotFoundError(userId: String, publicKeyId: String)
        extends ApiKeyDbError(
          message = s"Could not find API Key Data with userId = $userId and publicKeyId = $publicKeyId"
        )
    object ApiKeyDataNotFoundError {
      def apply(userId: String, publicKeyId: UUID): ApiKeyDataNotFoundError =
        ApiKeyDataNotFoundError(userId, publicKeyId.toString)
    }

    case object ApiKeyNotFoundError extends ApiKeyDbError(message = "Could not find API Key.")

  }

  sealed abstract class TenantDbError(override val message: String) extends CustomError
  object TenantDbError {

    sealed abstract class TenantInsertionError(override val message: String) extends TenantDbError(message)
    object TenantInsertionError {

      def tenantAlreadyExistsError(publicTenantId: String): TenantInsertionError =
        TenantAlreadyExistsError(publicTenantId)

      case class TenantAlreadyExistsError(publicTenantId: String)
          extends TenantInsertionError(
            message = s"Tenant with publicTenantId = $publicTenantId already exists."
          )
    }

    def tenantNotFoundError(publicTenantId: String): TenantDbError = TenantNotFoundError(publicTenantId)

    case class TenantNotFoundError(publicTenantId: String)
        extends TenantDbError(message = s"Could not find Tenant with publicTenantId = $publicTenantId")

    def tenantIsNotDeactivatedError(publicTenantId: UUID): TenantDbError = TenantIsNotDeactivatedError(publicTenantId)

    case class TenantIsNotDeactivatedError(publicTenantId: UUID)
        extends TenantDbError(
          message =
            s"Could not delete Tenant with publicTenantId = ${publicTenantId.toString} because it is not deactivated."
        )

  }
}
