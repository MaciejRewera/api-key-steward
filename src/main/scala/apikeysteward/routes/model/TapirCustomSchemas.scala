package apikeysteward.routes.model

import apikeysteward.routes.model.TapirCustomValidators.{ValidateList, ValidateOption}
import apikeysteward.routes.model.admin.apikey.{CreateApiKeyAdminRequest, UpdateApiKeyAdminRequest}
import apikeysteward.routes.model.admin.apikeytemplate.{CreateApiKeyTemplateRequest, UpdateApiKeyTemplateRequest}
import apikeysteward.routes.model.admin.apikeytemplatespermissions.{
  CreateApiKeyTemplatesPermissionsRequest,
  DeleteApiKeyTemplatesPermissionsRequest
}
import apikeysteward.routes.model.admin.apikeytemplatesusers.{
  AssociateApiKeyTemplatesWithUserRequest,
  AssociateUsersWithApiKeyTemplateRequest,
  DeleteApiKeyTemplatesFromUserRequest
}
import apikeysteward.routes.model.admin.application.{CreateApplicationRequest, UpdateApplicationRequest}
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import apikeysteward.routes.model.admin.tenant.{CreateTenantRequest, UpdateTenantRequest}
import apikeysteward.routes.model.admin.user.CreateUserRequest
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyExpirationCalculator.TtlTimeUnit
import sttp.tapir.{Schema, ValidationResult, Validator}

import scala.concurrent.duration.Duration

object TapirCustomSchemas {

  val createApiKeyRequestSchema: Schema[CreateApiKeyRequest] =
    Schema
      .derived[CreateApiKeyRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)
      .modify(_.ttl)(validateTtl)

  val createApiKeyAdminRequestSchema: Schema[CreateApiKeyAdminRequest] =
    Schema
      .derived[CreateApiKeyAdminRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)
      .modify(_.ttl)(validateTtl)
      .modify(_.userId)(validateUserId)

  val updateApiKeyAdminRequestSchema: Schema[UpdateApiKeyAdminRequest] =
    Schema
      .derived[UpdateApiKeyAdminRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)

  val createApiKeyTemplateRequestSchema: Schema[CreateApiKeyTemplateRequest] =
    Schema
      .derived[CreateApiKeyTemplateRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength280)
      .modify(_.description)(validateDescriptionLength500)
      .modify(_.apiKeyMaxExpiryPeriod)(validateApiKeyMaxExpiryPeriod)
      .modify(_.apiKeyPrefix)(validateApiKeyPrefix)

  val updateApiKeyTemplateRequestSchema: Schema[UpdateApiKeyTemplateRequest] =
    Schema
      .derived[UpdateApiKeyTemplateRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength280)
      .modify(_.description)(validateDescriptionLength500)
      .modify(_.apiKeyMaxExpiryPeriod)(validateApiKeyMaxExpiryPeriod)

  val createApiKeyTemplatesPermissionsRequestSchema: Schema[CreateApiKeyTemplatesPermissionsRequest] =
    Schema
      .derived[CreateApiKeyTemplatesPermissionsRequest]
      .modify(_.permissionIds)(validateListNotEmpty)

  val deleteApiKeyTemplatesPermissionsRequestSchema: Schema[DeleteApiKeyTemplatesPermissionsRequest] =
    Schema
      .derived[DeleteApiKeyTemplatesPermissionsRequest]
      .modify(_.permissionIds)(validateListNotEmpty)

  val associateUsersWithApiKeyTemplateRequestSchema: Schema[AssociateUsersWithApiKeyTemplateRequest] =
    Schema
      .derived[AssociateUsersWithApiKeyTemplateRequest]
      .modify(_.userIds)(validateListNotEmpty)
      .modify(_.userIds)(_.validateList(userIdValidator))

  val associateApiKeyTemplatesWithUserRequestSchema: Schema[AssociateApiKeyTemplatesWithUserRequest] =
    Schema
      .derived[AssociateApiKeyTemplatesWithUserRequest]
      .modify(_.templateIds)(validateListNotEmpty)
//      .modify(_.templateIds)(_.validateList(userIdValidator))

  val deleteApiKeyTemplatesWithUserRequestSchema: Schema[DeleteApiKeyTemplatesFromUserRequest] =
    Schema
      .derived[DeleteApiKeyTemplatesFromUserRequest]
      .modify(_.templateIds)(validateListNotEmpty)
//      .modify(_.templateIds)(_.validateList(userIdValidator))

  val createTenantRequestSchema: Schema[CreateTenantRequest] =
    Schema
      .derived[CreateTenantRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)

  val updateTenantRequestSchema: Schema[UpdateTenantRequest] =
    Schema
      .derived[UpdateTenantRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)

  lazy val createApplicationRequestSchema: Schema[CreateApplicationRequest] =
    Schema
      .derived[CreateApplicationRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)
      .modify(_.permissions)(_.validateList(createPermissionRequestSchema.validator))

  val updateApplicationRequestSchema: Schema[UpdateApplicationRequest] =
    Schema
      .derived[UpdateApplicationRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength250)
      .modify(_.description)(validateDescriptionLength250)

  val createPermissionRequestSchema: Schema[CreatePermissionRequest] =
    Schema
      .derived[CreatePermissionRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateNameLength280)
      .modify(_.description)(validateDescriptionLength500)

  val createUserRequestSchema: Schema[CreateUserRequest] =
    Schema
      .derived[CreateUserRequest]
      .map(Option(_))(trimStringFields)
      .modify(_.userId)(validateNameLength250)

  private def trimStringFields(request: CreateApiKeyRequest): CreateApiKeyRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateApiKeyAdminRequest): CreateApiKeyAdminRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim), userId = request.userId.trim)

  private def trimStringFields(request: UpdateApiKeyAdminRequest): UpdateApiKeyAdminRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateApiKeyTemplateRequest): CreateApiKeyTemplateRequest =
    request.copy(
      name = request.name.trim,
      description = request.description.map(_.trim),
      apiKeyPrefix = request.apiKeyPrefix.trim
    )

  private def trimStringFields(request: UpdateApiKeyTemplateRequest): UpdateApiKeyTemplateRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateTenantRequest): CreateTenantRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: UpdateTenantRequest): UpdateTenantRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateApplicationRequest): CreateApplicationRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: UpdateApplicationRequest): UpdateApplicationRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreatePermissionRequest): CreatePermissionRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateUserRequest): CreateUserRequest =
    request.copy(userId = request.userId.trim)

  private def validateNameLength250(schema: Schema[String]): Schema[String] = validateName(250)(schema)
  private def validateNameLength280(schema: Schema[String]): Schema[String] = validateName(280)(schema)

  private def validateName(maxLength: Int)(schema: Schema[String]): Schema[String] =
    schema.validate(Validator.nonEmptyString and Validator.maxLength(maxLength))

  private def validateDescriptionLength250(schema: Schema[Option[String]]): Schema[Option[String]] =
    validateDescription(250)(schema)
  private def validateDescriptionLength500(schema: Schema[Option[String]]): Schema[Option[String]] =
    validateDescription(500)(schema)

  private def validateDescription(maxLength: Int)(schema: Schema[Option[String]]): Schema[Option[String]] =
    schema.validateOption(Validator.nonEmptyString and Validator.maxLength(maxLength))

  private def validateApiKeyPrefix(schema: Schema[String]): Schema[String] =
    schema.validate(Validator.nonEmptyString and Validator.maxLength(120))

  private def validateUserId(schema: Schema[String]): Schema[String] =
    schema.validate(userIdValidator)

  private def userIdValidator: Validator[String] =
    Validator.nonEmptyString and Validator.maxLength(250)

  private def validateTtl(schema: Schema[Int]): Schema[Int] =
    schema
      .validate(Validator.positiveOrZero)
      .description(
        s"Time-to-live for the API Key in ${TtlTimeUnit.toString.toLowerCase}. Has to be positive or zero."
      )

  private def validateListNotEmpty[T](schema: Schema[List[T]]): Schema[List[T]] =
    schema.validate(Validator.nonEmpty)

  private def validateApiKeyMaxExpiryPeriod(schema: Schema[Duration]): Schema[Duration] = {
    val infValidator = Validator.custom[Duration](duration =>
      ValidationResult.validWhen(
        duration.ne(Duration.MinusInf) &&
          duration.ne(Duration.Undefined) &&
          (duration.eq(Duration.Inf) || duration.gteq(Duration.Zero))
      )
    )

    schema.validate(infValidator)
  }

}
