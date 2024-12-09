package apikeysteward.model.errors

import apikeysteward.model.ApiKeyTemplate.ApiKeyTemplateId
import apikeysteward.model.Tenant.TenantId
import apikeysteward.model.User.UserId
import apikeysteward.repositories.db.entity.ApiKeyTemplatesUsersEntity

import java.sql.SQLException
import java.util.UUID

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
