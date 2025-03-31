package apikeysteward.model.errors

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Permission.PermissionId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.model.errors.ApiKeysPermissionsDbError.ApiKeysPermissionsInsertionError

import java.sql.SQLException
import java.util.UUID

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

      def fromDbId(tenantId: UUID): ReferencedTenantDoesNotExistError =
        ReferencedTenantDoesNotExistErrorImpl(
          errorMessage = s"Tenant with ID = [${tenantId.toString}] does not exist."
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

    case class ApiKeyPermissionAssociationCannotBeCreated(error: ApiKeysPermissionsDbError)
        extends ApiKeyInsertionError(
          message = s"An error occurred when inserting API Key - Permission association: ${error.message}"
        )

    case class ApiKeyInsertionErrorImpl(cause: SQLException)
        extends ApiKeyInsertionError(message = s"An error occurred when inserting API Key: $cause")

  }

  trait ReferencedApiKeyTemplateDoesNotExistError extends ApiKeyInsertionError {
    val errorMessage: String
  }

  object ReferencedApiKeyTemplateDoesNotExistError {

    private case class ReferencedApiKeyTemplateDoesNotExistErrorImpl(override val errorMessage: String)
        extends ApiKeyInsertionError(errorMessage)
        with ReferencedApiKeyTemplateDoesNotExistError

    def fromDbId(apiKeyTemplateId: UUID): ReferencedApiKeyTemplateDoesNotExistError =
      ReferencedApiKeyTemplateDoesNotExistErrorImpl(
        errorMessage = s"ApiKeyTemplate with ID = [${apiKeyTemplateId.toString}] does not exist."
      )

    def apply(publicTemplateId: ApiKeyTemplateId): ReferencedApiKeyTemplateDoesNotExistError =
      ReferencedApiKeyTemplateDoesNotExistErrorImpl(
        errorMessage = s"ApiKeyTemplate with publicTemplateId = [$publicTemplateId] does not exist."
      )

  }

  trait ReferencedUserDoesNotExistError extends ApiKeyInsertionError {
    val errorMessage: String
  }

  object ReferencedUserDoesNotExistError {

    private case class ReferencedUserDoesNotExistErrorImpl(override val errorMessage: String)
        extends ApiKeyInsertionError(errorMessage)
        with ReferencedUserDoesNotExistError

    def fromDbId(userId: UUID): ReferencedUserDoesNotExistError =
      ReferencedUserDoesNotExistErrorImpl(
        errorMessage = s"User with ID = [${userId.toString}] does not exist."
      )

    def apply(publicUserId: UserId): ReferencedUserDoesNotExistError =
      ReferencedUserDoesNotExistErrorImpl(
        errorMessage = s"User with publicUserId = [$publicUserId] does not exist."
      )

  }

  trait ReferencedPermissionDoesNotExistError extends ApiKeyInsertionError {
    val errorMessage: String
  }

  object ReferencedPermissionDoesNotExistError {

    private case class ReferencedPermissionDoesNotExistErrorImpl(override val errorMessage: String)
        extends ApiKeyInsertionError(errorMessage)
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
