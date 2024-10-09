package apikeysteward.model

import apikeysteward.model.RepositoryErrors.ApiKeyDbError.ApiKeyDataNotFoundError

import java.sql.SQLException
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

      case class ReferencedApiKeyDoesNotExistError(apiKeyId: Long)
          extends ApiKeyInsertionError(message = s"ApiKey with id = [$apiKeyId] does not exist.")

      case class ApiKeyInsertionErrorImpl(cause: SQLException)
          extends ApiKeyInsertionError(
            message = s"An error occurred when inserting ApiKey: $cause"
          )
    }

    def apiKeyDataNotFoundError(userId: String, publicKeyId: UUID): ApiKeyDbError =
      apiKeyDataNotFoundError(userId, publicKeyId.toString)

    def apiKeyDataNotFoundError(userId: String, publicKeyId: String): ApiKeyDbError =
      ApiKeyDataNotFoundError(userId, publicKeyId)

    def apiKeyDataNotFoundError(publicKeyId: UUID): ApiKeyDbError =
      ApiKeyDataNotFoundError(publicKeyId)

    trait ApiKeyDataNotFoundError extends ApiKeyDbError { val errorMessage: String }
    object ApiKeyDataNotFoundError {

      private case class ApiKeyDataNotFoundErrorImpl(override val errorMessage: String)
          extends ApiKeyDbError(errorMessage)
          with ApiKeyDataNotFoundError

      def apply(userId: String, publicKeyId: UUID): ApiKeyDataNotFoundError =
        apply(userId, publicKeyId.toString)

      def apply(userId: String, publicKeyId: String): ApiKeyDataNotFoundError = ApiKeyDataNotFoundErrorImpl(
        errorMessage = s"Could not find API Key Data with userId = $userId and publicKeyId = $publicKeyId"
      )

      def apply(publicKeyId: UUID): ApiKeyDataNotFoundError =
        apply(publicKeyId.toString)
      def apply(publicKeyId: String): ApiKeyDataNotFoundError = ApiKeyDataNotFoundErrorImpl(
        errorMessage = s"Could not find API Key Data with publicKeyId = $publicKeyId"
      )
    }

    case object ApiKeyNotFoundError extends ApiKeyDbError(message = "Could not find API Key.")

  }

  sealed abstract class TenantDbError(override val message: String) extends CustomError
  object TenantDbError {

    sealed abstract class TenantInsertionError(override val message: String) extends TenantDbError(message)
    object TenantInsertionError {

      case class TenantAlreadyExistsError(publicTenantId: String)
          extends TenantInsertionError(
            message = s"Tenant with publicTenantId = $publicTenantId already exists."
          )

      case class TenantInsertionErrorImpl(cause: SQLException)
          extends TenantInsertionError(
            message = s"An error occurred when inserting Tenant: $cause"
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

  sealed abstract class ApplicationDbError(override val message: String) extends CustomError
  object ApplicationDbError {

    sealed abstract class ApplicationInsertionError(override val message: String) extends ApplicationDbError(message)
    object ApplicationInsertionError {

      case class ApplicationAlreadyExistsError(publicApplicationId: String)
          extends ApplicationInsertionError(
            message = s"Application with publicApplicationId = $publicApplicationId already exists."
          )

      trait ReferencedTenantDoesNotExistError extends ApplicationInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApplicationInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def apply(tenantId: Long): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with id = [$tenantId] does not exist."
        )
        def apply(publicTenantId: UUID): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
        )
      }

      case class ApplicationInsertionErrorImpl(cause: SQLException)
          extends ApplicationInsertionError(
            message = s"An error occurred when inserting Application: $cause"
          )
    }

    def applicationNotFoundError(publicApplicationId: String): ApplicationDbError =
      ApplicationNotFoundError(publicApplicationId)

    case class ApplicationNotFoundError(publicApplicationId: String)
        extends ApplicationDbError(
          message = s"Could not find Application with publicApplicationId = $publicApplicationId"
        )

    def applicationIsNotDeactivatedError(publicApplicationId: UUID): ApplicationDbError =
      ApplicationIsNotDeactivatedError(publicApplicationId)

    case class ApplicationIsNotDeactivatedError(publicApplicationId: UUID)
        extends ApplicationDbError(
          message =
            s"Could not delete Application with publicApplicationId = ${publicApplicationId.toString} because it is not deactivated."
        )

  }
}
