package apikeysteward.model

import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.Tenant.TenantId

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
          extends ApiKeyInsertionError(message = s"An error occurred when inserting ApiKey: $cause")
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
        errorMessage = s"Could not find API Key Data with userId = [$userId] and publicKeyId = [$publicKeyId]."
      )

      def apply(publicKeyId: UUID): ApiKeyDataNotFoundError =
        apply(publicKeyId.toString)
      def apply(publicKeyId: String): ApiKeyDataNotFoundError = ApiKeyDataNotFoundErrorImpl(
        errorMessage = s"Could not find API Key Data with publicKeyId = [$publicKeyId]."
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
            message = s"Tenant with publicTenantId = [$publicTenantId] already exists."
          )

      case class TenantInsertionErrorImpl(cause: SQLException)
          extends TenantInsertionError(message = s"An error occurred when inserting Tenant: $cause")
    }

    def tenantNotFoundError(publicTenantId: String): TenantDbError = TenantNotFoundError(publicTenantId)

    case class TenantNotFoundError(publicTenantId: String)
        extends TenantDbError(message = s"Could not find Tenant with publicTenantId = [$publicTenantId].")

    def tenantIsNotDeactivatedError(publicTenantId: TenantId): TenantDbError = TenantIsNotDeactivatedError(
      publicTenantId
    )

    case class TenantIsNotDeactivatedError(publicTenantId: TenantId)
        extends TenantDbError(
          message =
            s"Could not delete Tenant with publicTenantId = [${publicTenantId.toString}] because it is not deactivated."
        )
  }

  sealed abstract class ApplicationDbError(override val message: String) extends CustomError
  object ApplicationDbError {

    sealed abstract class ApplicationInsertionError(override val message: String) extends ApplicationDbError(message)
    object ApplicationInsertionError {

      case class ApplicationAlreadyExistsError(publicApplicationId: String)
          extends ApplicationInsertionError(
            message = s"Application with publicApplicationId = [$publicApplicationId] already exists."
          )

      trait ReferencedTenantDoesNotExistError extends ApplicationInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApplicationInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def apply(tenantId: Long): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with id = [$tenantId] does not exist."
        )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
        )
      }

      def cannotInsertPermissionError(
          publicApplicationId: ApplicationId,
          permissionInsertionError: PermissionInsertionError
      ): ApplicationInsertionError =
        CannotInsertPermissionError(publicApplicationId, permissionInsertionError)

      case class CannotInsertPermissionError(
          publicApplicationId: ApplicationId,
          permissionInsertionError: PermissionInsertionError
      ) extends ApplicationInsertionError(
            message =
              s"Could not insert Permissions for Application with publicApplicationId = [$publicApplicationId], because: $permissionInsertionError"
          )

      case class ApplicationInsertionErrorImpl(cause: SQLException)
          extends ApplicationInsertionError(message = s"An error occurred when inserting Application: $cause")
    }

    def applicationNotFoundError(publicApplicationId: String): ApplicationDbError =
      ApplicationNotFoundError(publicApplicationId)

    case class ApplicationNotFoundError(publicApplicationId: String)
        extends ApplicationDbError(
          message = s"Could not find Application with publicApplicationId = [$publicApplicationId]."
        )

    def applicationIsNotDeactivatedError(publicApplicationId: ApplicationId): ApplicationDbError =
      ApplicationIsNotDeactivatedError(publicApplicationId)

    case class ApplicationIsNotDeactivatedError(publicApplicationId: ApplicationId)
        extends ApplicationDbError(
          message =
            s"Could not delete Application with publicApplicationId = [${publicApplicationId.toString}] because it is not deactivated."
        )

    def cannotDeletePermissionError(
        publicApplicationId: ApplicationId,
        permissionNotFoundError: PermissionNotFoundError
    ): ApplicationDbError =
      CannotDeletePermissionError(publicApplicationId, permissionNotFoundError)

    case class CannotDeletePermissionError(
        publicApplicationId: ApplicationId,
        permissionNotFoundError: PermissionNotFoundError
    ) extends ApplicationDbError(
          message =
            s"Could not delete Permissions for Application with publicApplicationId = [$publicApplicationId], because: $permissionNotFoundError"
        )

  }

  sealed abstract class PermissionDbError(override val message: String) extends CustomError
  object PermissionDbError {

    sealed abstract class PermissionInsertionError(override val message: String) extends PermissionDbError(message)
    object PermissionInsertionError {

      case class PermissionAlreadyExistsError(publicPermissionId: String)
          extends PermissionInsertionError(
            message = s"Permission with publicPermissionId = [$publicPermissionId] already exists."
          )

      case class PermissionAlreadyExistsForThisApplicationError(permissionName: String, applicationId: Long)
          extends PermissionInsertionError(
            message =
              s"Permission with name = $permissionName already exists for Application with ID = [$applicationId]."
          )

      trait ReferencedApplicationDoesNotExistError extends PermissionInsertionError { val errorMessage: String }
      object ReferencedApplicationDoesNotExistError {

        private case class ReferencedApplicationDoesNotExistErrorImpl(override val errorMessage: String)
            extends PermissionInsertionError(errorMessage)
            with ReferencedApplicationDoesNotExistError

        def apply(applicationId: Long): ReferencedApplicationDoesNotExistError =
          ReferencedApplicationDoesNotExistErrorImpl(
            errorMessage = s"Application with id = [$applicationId] does not exist."
          )
        def apply(publicApplicationId: ApplicationId): ReferencedApplicationDoesNotExistError =
          ReferencedApplicationDoesNotExistErrorImpl(
            errorMessage = s"Application with publicApplicationId = [$publicApplicationId] does not exist."
          )
      }

      case class PermissionInsertionErrorImpl(cause: SQLException)
          extends PermissionInsertionError(message = s"An error occurred when inserting Permission: $cause")
    }

    case class PermissionNotFoundError(publicApplicationId: ApplicationId, publicPermissionId: PermissionId)
        extends PermissionDbError(
          message =
            s"Could not find Permission with publicPermissionId = [$publicPermissionId] for Application with publicApplicationId = [$publicApplicationId]."
        )
  }

}
