package apikeysteward.routes.model

import apikeysteward.routes.model.TapirCustomValidators.ValidateOption
import apikeysteward.routes.model.admin.tenant.{CreateTenantRequest, CreateTenantResponse, UpdateTenantRequest}
import apikeysteward.routes.model.admin.UpdateApiKeyRequest
import apikeysteward.services.ApiKeyExpirationCalculator.ttlTimeUnit
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.{Schema, Validator}

object TapirCustomSchemas {

  val createApiKeyRequestSchema: Schema[CreateApiKeyRequest] =
    implicitly[Derived[Schema[CreateApiKeyRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName)
      .modify(_.description)(validateDescription)
      .modify(_.ttl)(validateTtl)

  val updateApiKeyRequestSchema: Schema[UpdateApiKeyRequest] =
    implicitly[Derived[Schema[UpdateApiKeyRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName)
      .modify(_.description)(validateDescription)

  val createTenantRequestSchema: Schema[CreateTenantRequest] =
    implicitly[Derived[Schema[CreateTenantRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName)

  val updateTenantRequestSchema: Schema[UpdateTenantRequest] =
    implicitly[Derived[Schema[UpdateTenantRequest]]].value
      .map(Option(_))(trimStringFields)
      .modify(_.name)(validateName)

  private def trimStringFields(request: CreateApiKeyRequest): CreateApiKeyRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: UpdateApiKeyRequest): UpdateApiKeyRequest =
    request.copy(name = request.name.trim, description = request.description.map(_.trim))

  private def trimStringFields(request: CreateTenantRequest): CreateTenantRequest =
    request.copy(name = request.name.trim)

  private def trimStringFields(request: UpdateTenantRequest): UpdateTenantRequest =
    request.copy(name = request.name.trim)

  private def validateName(schema: Schema[String]): Schema[String] =
    schema.validate(Validator.nonEmptyString and Validator.maxLength(250))

  private def validateDescription(schema: Schema[Option[String]]): Schema[Option[String]] =
    schema.validateOption(Validator.nonEmptyString and Validator.maxLength(250))

  private def validateTtl(schema: Schema[Int]): Schema[Int] =
    schema
      .validate(Validator.positiveOrZero)
      .description(
        s"Time-to-live for the API Key in ${ttlTimeUnit.toString.toLowerCase}. Has to be positive or zero."
      )

}
