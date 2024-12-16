package apikeysteward.routes.model

import sttp.tapir.{Schema, ValidationResult, Validator}
import scala.concurrent.duration.Duration

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

  implicit class ValidateList[T](schema: Schema[List[T]]) {
    def validateList(validator: Validator[T]): Schema[List[T]] =
      schema.validate(
        Validator.custom[List[T]](list => ValidationResult.validWhen(list.flatMap(elem => validator(elem)).isEmpty))
      )
  }

  val durationInfiniteOrGreaterThanZeroValidator: Validator[Duration] =
    Validator.custom[Duration](duration =>
      ValidationResult.validWhen(
        duration.ne(Duration.MinusInf) &&
          duration.ne(Duration.Undefined) &&
          (duration.eq(Duration.Inf) || duration.gteq(Duration.Zero))
      )
    )

}
