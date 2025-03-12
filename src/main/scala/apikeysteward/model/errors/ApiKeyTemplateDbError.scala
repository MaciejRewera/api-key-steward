package apikeysteward.model.errors

import apikeysteward.model.Tenant.TenantId

import java.sql.SQLException
import java.util.UUID

sealed abstract class ApiKeyTemplateDbError(override val message: String) extends CustomError
object ApiKeyTemplateDbError {

  sealed abstract class ApiKeyTemplateInsertionError(override val message: String)
      extends ApiKeyTemplateDbError(message)
  object ApiKeyTemplateInsertionError {

    case class IncorrectRandomSectionLength(randomSectionLength: Int)
        extends ApiKeyTemplateInsertionError(
          message =
            s"Provided [CreateApiKeyTemplateRequest.randomSectionLength] has value not greater than zero: [$randomSectionLength]."
        )

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
