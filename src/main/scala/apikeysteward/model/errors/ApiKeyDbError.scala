package apikeysteward.model.errors

import apikeysteward.model.Tenant.TenantId

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
