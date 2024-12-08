package apikeysteward.model

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.RepositoryErrors.PermissionDbError.{PermissionInsertionError, PermissionNotFoundError}
import apikeysteward.model.ResourceServer.ResourceServerId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.{ApiKeyTemplatesPermissionsEntity, ApiKeyTemplatesUsersEntity}

import java.sql.SQLException
import java.util.UUID

object RepositoryErrors {

  sealed abstract class GenericError(override val message: String) extends CustomError
  object GenericError {

    case class ApiKeyTemplateDoesNotExistError(publicTenantId: TenantId, publicApiKeyTemplateId: ApiKeyTemplateId)
        extends GenericError(
          message =
            s"ApiKeyTemplate with publicTemplateId = [$publicApiKeyTemplateId] does not exist for Tenant with publicTenantId = [$publicTenantId]."
        )

    case class UserDoesNotExistError(publicTenantId: TenantId, publicUserId: UserId)
        extends GenericError(
          message =
            s"User with publicUserId = [$publicUserId] does not exist for Tenant with publicTenantId = [$publicTenantId]."
        )

  }

  sealed abstract class ApiKeyDbError(override val message: String) extends CustomError
  object ApiKeyDbError {

    sealed abstract class ApiKeyInsertionError(override val message: String) extends ApiKeyDbError(message)
    object ApiKeyInsertionError {

      trait ReferencedTenantDoesNotExistError extends ApiKeyInsertionError {
        val errorMessage: String
      }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def fromDbId(tenantDbId: UUID): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [${tenantDbId.toString}] does not exist."
          )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
          )
      }

      case object ApiKeyAlreadyExistsError extends ApiKeyInsertionError(message = "API Key already exists.")

      case object ApiKeyIdAlreadyExistsError
          extends ApiKeyInsertionError(message = "API Key Data with the same apiKeyId already exists.")

      case object PublicKeyIdAlreadyExistsError
          extends ApiKeyInsertionError(message = "API Key Data with the same publicKeyId already exists.")

      case class ReferencedApiKeyDoesNotExistError(apiKeyId: UUID)
          extends ApiKeyInsertionError(message = s"ApiKey with id = [${apiKeyId.toString}] does not exist.")

      case class ApiKeyInsertionErrorImpl(cause: SQLException)
          extends ApiKeyInsertionError(message = s"An error occurred when inserting ApiKey: $cause")
    }

    def apiKeyDataNotFoundError(userId: UserId, publicKeyId: UUID): ApiKeyDbError =
      apiKeyDataNotFoundError(userId, publicKeyId.toString)

    def apiKeyDataNotFoundError(userId: UserId, publicKeyId: String): ApiKeyDbError =
      ApiKeyDataNotFoundError(userId, publicKeyId)

    def apiKeyDataNotFoundError(publicTenantId: TenantId, publicKeyId: UUID): ApiKeyDbError =
      ApiKeyDataNotFoundError(publicTenantId, publicKeyId)

    trait ApiKeyDataNotFoundError extends ApiKeyDbError { val errorMessage: String }
    object ApiKeyDataNotFoundError {

      private case class ApiKeyDataNotFoundErrorImpl(override val errorMessage: String)
          extends ApiKeyDbError(errorMessage)
          with ApiKeyDataNotFoundError

      def apply(userId: UserId, publicKeyId: UUID): ApiKeyDataNotFoundError =
        apply(userId, publicKeyId.toString)

      def apply(userId: UserId, publicKeyId: String): ApiKeyDataNotFoundError = ApiKeyDataNotFoundErrorImpl(
        errorMessage = s"Could not find API Key Data for userId = [$userId] and publicKeyId = [$publicKeyId]."
      )

      def apply(publicTenantId: TenantId, publicKeyId: UUID): ApiKeyDataNotFoundError =
        apply(publicTenantId, publicKeyId.toString)
      def apply(publicTenantId: TenantId, publicKeyId: String): ApiKeyDataNotFoundError = ApiKeyDataNotFoundErrorImpl(
        errorMessage =
          s"Could not find API Key Data with publicKeyId = [$publicKeyId] for Tenant with publicTenantId = [$publicTenantId]."
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

    def tenantIsNotDeactivatedError(publicTenantId: TenantId): TenantDbError =
      TenantIsNotDeactivatedError(publicTenantId)

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

  sealed abstract class ResourceServerDbError(override val message: String) extends CustomError
  object ResourceServerDbError {

    sealed abstract class ResourceServerInsertionError(override val message: String)
        extends ResourceServerDbError(message)
    object ResourceServerInsertionError {

      case class ResourceServerAlreadyExistsError(publicResourceServerId: String)
          extends ResourceServerInsertionError(
            message = s"ResourceServer with publicResourceServerId = [$publicResourceServerId] already exists."
          )

      trait ReferencedTenantDoesNotExistError extends ResourceServerInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ResourceServerInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
        )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError = ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
        )
      }

      def cannotInsertPermissionError(
          publicResourceServerId: ResourceServerId,
          permissionInsertionError: PermissionInsertionError
      ): ResourceServerInsertionError =
        CannotInsertPermissionError(publicResourceServerId, permissionInsertionError)

      case class CannotInsertPermissionError(
          publicResourceServerId: ResourceServerId,
          permissionInsertionError: PermissionInsertionError
      ) extends ResourceServerInsertionError(
            message =
              s"Could not insert Permissions for ResourceServer with publicResourceServerId = [$publicResourceServerId], because: $permissionInsertionError"
          )

      case class ResourceServerInsertionErrorImpl(cause: SQLException)
          extends ResourceServerInsertionError(message = s"An error occurred when inserting ResourceServer: $cause")
    }

    def resourceServerNotFoundError(publicResourceServerId: ResourceServerId): ResourceServerDbError =
      resourceServerNotFoundError(publicResourceServerId.toString)

    def resourceServerNotFoundError(publicResourceServerId: String): ResourceServerDbError =
      ResourceServerNotFoundError(publicResourceServerId)

    case class ResourceServerNotFoundError(publicResourceServerId: String)
        extends ResourceServerDbError(
          message = s"Could not find ResourceServer with publicResourceServerId = [$publicResourceServerId]."
        )

    def resourceServerIsNotDeactivatedError(publicResourceServerId: ResourceServerId): ResourceServerDbError =
      ResourceServerIsNotDeactivatedError(publicResourceServerId)

    case class ResourceServerIsNotDeactivatedError(publicResourceServerId: ResourceServerId)
        extends ResourceServerDbError(
          message =
            s"Could not delete ResourceServer with publicResourceServerId = [${publicResourceServerId.toString}] because it is not deactivated."
        )

    def cannotDeletePermissionError(
        publicResourceServerId: ResourceServerId,
        permissionNotFoundError: PermissionNotFoundError
    ): ResourceServerDbError =
      CannotDeletePermissionError(publicResourceServerId, permissionNotFoundError)

    case class CannotDeletePermissionError(
        publicResourceServerId: ResourceServerId,
        permissionNotFoundError: PermissionNotFoundError
    ) extends ResourceServerDbError(
          message =
            s"Could not delete Permissions for ResourceServer with publicResourceServerId = [$publicResourceServerId], because: $permissionNotFoundError"
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

      case class PermissionAlreadyExistsForThisResourceServerError(permissionName: String, resourceServerId: UUID)
          extends PermissionInsertionError(
            message =
              s"Permission with name = $permissionName already exists for ResourceServer with ID = [${resourceServerId.toString}]."
          )

      trait ReferencedTenantDoesNotExistError extends PermissionInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends PermissionInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
          )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
          )
      }

      trait ReferencedResourceServerDoesNotExistError extends PermissionInsertionError { val errorMessage: String }
      object ReferencedResourceServerDoesNotExistError {

        private case class ReferencedResourceServerDoesNotExistErrorImpl(override val errorMessage: String)
            extends PermissionInsertionError(errorMessage)
            with ReferencedResourceServerDoesNotExistError

        def fromDbId(resourceServerId: UUID): ReferencedResourceServerDoesNotExistError =
          ReferencedResourceServerDoesNotExistErrorImpl(
            errorMessage = s"ResourceServer with ID = [${resourceServerId.toString}] does not exist."
          )
        def apply(publicResourceServerId: ResourceServerId): ReferencedResourceServerDoesNotExistError =
          ReferencedResourceServerDoesNotExistErrorImpl(
            errorMessage = s"ResourceServer with publicResourceServerId = [$publicResourceServerId] does not exist."
          )
      }

      case class PermissionInsertionErrorImpl(cause: SQLException)
          extends PermissionInsertionError(message = s"An error occurred when inserting Permission: $cause")
    }

    case class PermissionNotFoundError(publicResourceServerId: ResourceServerId, publicPermissionId: PermissionId)
        extends PermissionDbError(
          message =
            s"Could not find Permission with publicPermissionId = [$publicPermissionId] for ResourceServer with publicResourceServerId = [$publicResourceServerId]."
        )
  }

  sealed abstract class UserDbError(override val message: String) extends CustomError
  object UserDbError {

    sealed abstract class UserInsertionError(override val message: String) extends UserDbError(message)
    object UserInsertionError {

      case class UserAlreadyExistsForThisTenantError(publicUserId: UserId, tenantId: UUID)
          extends UserInsertionError(
            message =
              s"User with publicUserId = $publicUserId already exists for Tenant with ID = [${tenantId.toString}]."
          )

      trait ReferencedTenantDoesNotExistError extends UserInsertionError { val errorMessage: String }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends UserInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
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

        def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
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

      case class ApiKeyTemplatesPermissionsAlreadyExistsError(apiKeyTemplateId: UUID, permissionId: UUID)
          extends ApiKeyTemplatesPermissionsInsertionError(
            message =
              s"ApiKeyTemplatesPermissions with apiKeyTemplateId = [${apiKeyTemplateId.toString}] and permissionId = [${permissionId.toString}] already exists."
          )

      trait ReferencedTenantDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
        val errorMessage: String
      }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
          )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
          )
      }

      trait ReferencedApiKeyTemplateDoesNotExistError extends ApiKeyTemplatesPermissionsInsertionError {
        val errorMessage: String
      }
      object ReferencedApiKeyTemplateDoesNotExistError {

        private case class ReferencedApiKeyTemplateDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesPermissionsInsertionError(errorMessage)
            with ReferencedApiKeyTemplateDoesNotExistError

        def fromDbId(apiKeyTemplateId: UUID): ReferencedApiKeyTemplateDoesNotExistError =
          ReferencedApiKeyTemplateDoesNotExistErrorImpl(
            errorMessage = s"ApiKeyTemplate with ID = [${apiKeyTemplateId.toString}] does not exist."
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

        def fromDbId(permissionId: UUID): ReferencedPermissionDoesNotExistError =
          ReferencedPermissionDoesNotExistErrorImpl(
            errorMessage = s"Permission with ID = [${permissionId.toString}] does not exist."
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

      case class ApiKeyTemplatesUsersAlreadyExistsError(apiKeyTemplateId: UUID, userId: UUID)
          extends ApiKeyTemplatesUsersInsertionError(
            message =
              s"ApiKeyTemplatesUsers with apiKeyTemplateId = [${apiKeyTemplateId.toString}] and userId = [${userId.toString}] already exists."
          )

      trait ReferencedTenantDoesNotExistError extends ApiKeyTemplatesUsersInsertionError {
        val errorMessage: String
      }
      object ReferencedTenantDoesNotExistError {

        private case class ReferencedTenantDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesUsersInsertionError(errorMessage)
            with ReferencedTenantDoesNotExistError

        def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
          )
        def apply(publicTenantId: TenantId): ReferencedTenantDoesNotExistError =
          ReferencedTenantDoesNotExistErrorImpl(
            errorMessage = s"Tenant with publicTenantId = [$publicTenantId] does not exist."
          )
      }

      trait ReferencedApiKeyTemplateDoesNotExistError extends ApiKeyTemplatesUsersInsertionError {
        val errorMessage: String
      }
      object ReferencedApiKeyTemplateDoesNotExistError {

        private case class ReferencedApiKeyTemplateDoesNotExistErrorImpl(override val errorMessage: String)
            extends ApiKeyTemplatesUsersInsertionError(errorMessage)
            with ReferencedApiKeyTemplateDoesNotExistError

        def fromDbId(apiKeyTemplateId: UUID): ReferencedApiKeyTemplateDoesNotExistError =
          ReferencedApiKeyTemplateDoesNotExistErrorImpl(
            errorMessage = s"ApiKeyTemplate with ID = [${apiKeyTemplateId.toString}] does not exist."
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

        def fromDbId(userId: UUID): ReferencedUserDoesNotExistError =
          ReferencedUserDoesNotExistErrorImpl(
            errorMessage = s"User with ID = [${userId.toString}] does not exist."
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
