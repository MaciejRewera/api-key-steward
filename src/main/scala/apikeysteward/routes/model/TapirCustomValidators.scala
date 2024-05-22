package apikeysteward.routes.model

import sttp.tapir.{Schema, ValidationResult, Validator}

object TapirCustomValidators {

  implicit class ValidateOption[T](schema: Schema[Option[T]]) {
    def validateOption(validator: Validator[T]): Schema[Option[T]] =
      schema.validate(
        Validator.custom[Option[T]] {
          case Some(value) => ValidationResult.validWhen(validator(value).isEmpty)
          case None        => ValidationResult.Valid
        }
      )
  }
}
