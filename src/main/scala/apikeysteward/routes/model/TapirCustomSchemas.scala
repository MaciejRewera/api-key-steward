package apikeysteward.routes.model

import apikeysteward.routes.model.TapirCustomValidators.ValidateOption
import apikeysteward.routes.model.admin.apikey.{CreateApiKeyAdminRequest, UpdateApiKeyAdminRequest}
import apikeysteward.routes.model.admin.application.{CreateApplicationRequest, UpdateApplicationRequest}
import apikeysteward.routes.model.admin.permission.CreatePermissionRequest
import apikeysteward.routes.model.admin.tenant.{CreateTenantRequest, UpdateTenantRequest}
import apikeysteward.routes.model.apikey.CreateApiKeyRequest
import apikeysteward.services.ApiKeyExpirationCalculator.TtlTimeUnit
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.{Schema, Validator}

object TapirCustomSchemas {

  val createApiKeyRequestSchema: Schema[CreateApiKeyRequest] =
    implicitly[Derived[Schema[CreateApiKeyRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)
      .modify(_.ttl)(validateTtl)

  val createApiKeyAdminRequestSchema: Schema[CreateApiKeyAdminRequest] =
    implicitly[Derived[Schema[CreateApiKeyAdminRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)
      .modify(_.ttl)(validateTtl)
      .modify(_.userId)(validateUserId)

  val updateApiKeyAdminRequestSchema: Schema[UpdateApiKeyAdminRequest] =
    implicitly[Derived[Schema[UpdateApiKeyAdminRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)

  val createTenantRequestSchema: Schema[CreateTenantRequest] =
    implicitly[Derived[Schema[CreateTenantRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)

  val updateTenantRequestSchema: Schema[UpdateTenantRequest] =
    implicitly[Derived[Schema[UpdateTenantRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)

  val createApplicationRequestSchema: Schema[CreateApplicationRequest] =
    implicitly[Derived[Schema[CreateApplicationRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)

  val updateApplicationRequestSchema: Schema[UpdateApplicationRequest] =
    implicitly[Derived[Schema[UpdateApplicationRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName250)
      .modify(_.description)(validateDescription)

  val createPermissionRequestSchema: Schema[CreatePermissionRequest] =
    implicitly[Derived[Schema[CreatePermissionRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName120)
      .modify(_.description)(validateDescription)

  private def trimStringFields(request: CreateApiKeyRequest): CreateApiKeyRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateApiKeyAdminRequest): CreateApiKeyAdminRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim), userId = request.userId.trim)

  private def trimStringFields(request: UpdateApiKeyAdminRequest): UpdateApiKeyAdminRequest =
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

  private def validateName250(schema: Schema[String]): Schema[String] = validateName(250)(schema)
  private def validateName120(schema: Schema[String]): Schema[String] = validateName(120)(schema)

  private def validateName(maxLength: Int)(schema: Schema[String]): Schema[String] =
    schema.validate(Validator.nonEmptyString and Validator.maxLength(maxLength))

  private def validateDescription(schema: Schema[Option[String]]): Schema[Option[String]] =
    schema.validateOption(Validator.nonEmptyString and Validator.maxLength(250))

  private def validateUserId(schema: Schema[String]): Schema[String] =
    schema.validate(Validator.nonEmptyString and Validator.maxLength(250))

  private def validateTtl(schema: Schema[Int]): Schema[Int] =
    schema
      .validate(Validator.positiveOrZero)
      .description(
        s"Time-to-live for the API Key in ${TtlTimeUnit.toString.toLowerCase}. Has to be positive or zero."
      )

}
