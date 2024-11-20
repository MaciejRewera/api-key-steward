package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Application.ApplicationId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesPermissionsEntity, ApiKeyTemplatesUsersEntity}

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

    def tenantNotFoundError(publicTenantId: TenantId): TenantDbError = tenantNotFoundError(publicTenantId.toString)
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

    def cannotDeleteDependencyError(publicTenantId: TenantId, dependencyError: CustomError): TenantDbError =
      CannotDeleteDependencyError(publicTenantId, dependencyError)

    case class CannotDeleteDependencyError(publicTenantId: TenantId, dependencyError: CustomError)
        extends TenantDbError(
          message =
            s"Could not delete Tenant with publicTenantId = [${publicTenantId.toString}] because one of its dependencies cannot be deleted: ${dependencyError.message}"
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
          errorMessage = s"Tenant with ID = [$tenantId] does not exist."
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

    def applicationNotFoundError(publicApplicationId: ApplicationId): ApplicationDbError =
      applicationNotFoundError(publicApplicationId.toString)

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
            errorMessage = s"Application with ID = [$applicationId] does not exist."
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

  sealed abstract class UserDbError(override val message: String) extends CustomError
  object UserDbError {

    sealed abstract class UserInsertionError(override val message: String) extends UserDbError(message)
    object UserInsertionError {

      case class UserAlreadyExistsForThisTenantError(publicUserId: UserId, tenantId: Long)
          extends UserInsertionError(
            message = s"User with publicUserId = $publicUserId already exists for Tenant with ID = [$tenantId]."
          )

      trait ReferencedTenantDoesNotExistError extends UserInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends UserInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def apply(tenantId: Long): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [$tenantId] does not exist."
          )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
          )
      }

      case class UserInsertionErrorImpl(cause: SQLException)
          extends UserInsertionError(message = s"An error occurred when inserting User: $cause")
    }

    case class UserNotFoundError(publicTenantId: TenantId, publicUserId: UserId)
        extends UserDbError(
          message =
            s"Could not find User with publicUserId = [$publicUserId] for Tenant with publicTenantId = [$publicTenantId]."
        )
  }

  sealed abstract class ApiKeyTemplateDbError(override val message: String) extends CustomError
  object ApiKeyTemplateDbError {

    sealed abstract class ApiKeyTemplateInsertionError(override val message: String)
        extends ApiKeyTemplateDbError(message)
    object ApiKeyTemplateInsertionError {

      case class ApiKeyTemplateAlreadyExistsError(publicTemplateId: String)
          extends ApiKeyTemplateInsertionError(
            message = s"ApiKeyTemplate with publicTemplateId = [$publicTemplateId] already exists."
          )

      trait ReferencedTenantDoesNotExistError extends ApiKeyTemplateInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplateInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def apply(tenantId: Long): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [$tenantId] does not exist."
          )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
          )
      }

      case class ApiKeyTemplateInsertionErrorImpl(cause: SQLException)
          extends ApiKeyTemplateInsertionError(message = s"An error occurred when inserting ApiKeyTemplate: $cause")
    }

    case class ApiKeyTemplateNotFoundError(publicTemplateId: String)
        extends ApiKeyTemplateDbError(
          message = s"Could not find ApiKeyTemplate with publicTemplateId = [$publicTemplateId]."
        )
  }

  sealed abstract class ApiKeyTemplatesPermissionsDbError(override val message: String) extends CustomError
  object ApiKeyTemplatesPermissionsDbError {

    sealed abstract class ApiKeyTemplatesPermissionsInsertionError(override val message: String)
        extends ApiKeyTemplatesPermissionsDbError(message)
    object ApiKeyTemplatesPermissionsInsertionError {

      case class ApiKeyTemplatesPermissionsInsertionErrorImpl(cause: SQLException)
          extends ApiKeyTemplatesPermissionsInsertionError(
            message = s"An error occurred when inserting ApiKeyTemplatesPermissions: $cause"
          )

      case class ApiKeyTemplatesPermissionsAlreadyExistsError(apiKeyTemplateId: Long, permissionId: Long)
          extends ApiKeyTemplatesPermissionsInsertionError(
            message =
              s"ApiKeyTemplatesPermissions with apiKeyTemplateId = [$apiKeyTemplateId] and permissionId = [$permissionId] already exists."
          )

      trait ReferencedApiKeyTemplateDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
        val errorMessage: String
      }
      object ReferencedApiKeyTemplateDoesNotExistError {

        private case class ReferencedApiKeyTemplateDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
            with ReferencedApiKeyTemplateDoesNotExistError

        def apply(apiKeyTemplateId: Long): ReferencedApiKeyTemplateDoesNotExistError =
          ReferencedApiKeyTemplateDoesNotExistErrorImpl(
            errorMessage = s"ApiKeyTemplate with ID = [$apiKeyTemplateId] does not exist."
          )
        def apply(publicApiKeyTemplateId: ApiKeyTemplateId): ReferencedApiKeyTemplateDoesNotExistError =
          ReferencedApiKeyTemplateDoesNotExistErrorImpl(
            errorMessage = s"ApiKeyTemplate with publicTemplateId = [$publicApiKeyTemplateId] does not exist."
          )
      }

      trait ReferencedPermissionDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
        val errorMessage: String
      }
      object ReferencedPermissionDoesNotExistError {

        private case class ReferencedPermissionDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
            with ReferencedPermissionDoesNotExistError

        def apply(permissionId: Long): ReferencedPermissionDoesNotExistError =
          ReferencedPermissionDoesNotExistErrorImpl(
            errorMessage = s"Permission with ID = [$permissionId] does not exist."
          )
        def apply(publicPermissionId: PermissionId): ReferencedPermissionDoesNotExistError =
          ReferencedPermissionDoesNotExistErrorImpl(
            errorMessage = s"Permission with publicPermissionId = [$publicPermissionId] does not exist."
          )
      }
    }

    case class ApiKeyTemplatesPermissionsNotFoundError(missingEntities: List[ApiKeyTemplatesPermissionsEntity.Write])
        extends ApiKeyTemplatesPermissionsDbError(
          message = {
            val missingEntitiesFormatted =
              missingEntities.map(e => (e.apiKeyTemplateId, e.permissionId).toString).mkString("[", ", ", "]")

            s"Could not find ApiKeyTemplatesPermissions with (apiKeyTemplateId, permissionId): $missingEntitiesFormatted."
          }
        )
  }

  sealed abstract class ApiKeyTemplatesUsersDbError(override val message: String) extends CustomError
  object ApiKeyTemplatesUsersDbError {

    sealed abstract class ApiKeyTemplatesUsersInsertionError(override val message: String)
        extends ApiKeyTemplatesUsersDbError(message)
    object ApiKeyTemplatesUsersInsertionError {

      case class ApiKeyTemplatesUsersInsertionErrorImpl(cause: SQLException)
          extends ApiKeyTemplatesUsersInsertionError(
            message = s"An error occurred when inserting ApiKeyTemplatesUsers: $cause"
          )

      case class ApiKeyTemplatesUsersAlreadyExistsError(apiKeyTemplateId: Long, userId: Long)
          extends ApiKeyTemplatesUsersInsertionError(
            message =
              s"ApiKeyTemplatesUsers with apiKeyTemplateId = [$apiKeyTemplateId] and userId = [$userId] already exists."
          )

      trait ReferencedApiKeyTemplateDoesNotExistError extends ApiKeyTemplatesUsersInsertionError {
        val errorMessage: String
      }
      object ReferencedApiKeyTemplateDoesNotExistError {

        private case class ReferencedApiKeyTemplateDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesUsersInsertionError(errorMessage)
            with ReferencedApiKeyTemplateDoesNotExistError

        def apply(apiKeyTemplateId: Long): ReferencedApiKeyTemplateDoesNotExistError =
          ReferencedApiKeyTemplateDoesNotExistErrorImpl(
            errorMessage = s"ApiKeyTemplate with ID = [$apiKeyTemplateId] does not exist."
          )
        def apply(publicApiKeyTemplateId: ApiKeyTemplateId): ReferencedApiKeyTemplateDoesNotExistError =
          ReferencedApiKeyTemplateDoesNotExistErrorImpl(
            errorMessage = s"ApiKeyTemplate with publicTemplateId = [$publicApiKeyTemplateId] does not exist."
          )
      }

      trait ReferencedUserDoesNotExistError extends ApiKeyTemplatesUsersInsertionError {
        val errorMessage: String
      }
      object ReferencedUserDoesNotExistError {

        private case class ReferencedUserDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesUsersInsertionError(errorMessage)
            with ReferencedUserDoesNotExistError

        def apply(userId: Long): ReferencedUserDoesNotExistError =
          ReferencedUserDoesNotExistErrorImpl(
            errorMessage = s"User with ID = [$userId] does not exist."
          )
        def apply(publicUserId: UserId, publicTenantId: TenantId): ReferencedUserDoesNotExistError =
          ReferencedUserDoesNotExistErrorImpl(
            errorMessage =
              s"User with publicUserId = [$publicUserId] does not exist for Tenant with publicTenantId = [$publicTenantId]."
          )
      }
    }

    case class ApiKeyTemplatesUsersNotFoundError(missingEntities: List[ApiKeyTemplatesUsersEntity.Write])
        extends ApiKeyTemplatesUsersDbError(
          message = {
            val missingEntitiesFormatted =
              missingEntities.map(e => (e.apiKeyTemplateId, e.userId).toString).mkString("[", ", ", "]")

            s"Could not find ApiKeyTemplatesUsers with (apiKeyTemplateId, userId): $missingEntitiesFormatted."
          }
        )
  }

}
