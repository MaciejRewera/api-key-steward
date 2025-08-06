package apikeysteward.model.errors

import java.sql.SQLException

sealed abstract class Auth0LoginDbError(override val message: String) extends CustomError

object Auth0LoginDbError {

  case class Auth0LoginDbErrorImpl(cause: SQLException)
      extends Auth0LoginDbError(message = s"An error occurred when upserting Auth0Login: $cause")

}
