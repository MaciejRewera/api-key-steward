package apikeysteward.repositories.db

import java.sql.SQLException
import java.util.UUID

private[db] object ForeignKeyViolationSqlErrorExtractor {

  def extractColumnUuidValue(sqlException: SQLException)(columnName: String): UUID = {
    val rawId = sqlException.getMessage
      .split(s"\\($columnName\\)=\\(")
      .drop(1)
      .head
      .takeWhile(_ != ')')
      .trim

    UUID.fromString(rawId)
  }

  def extractTwoColumnsUuidValues(
      sqlException: SQLException
  )(columnName_1: String, columnName_2: String): (UUID, UUID) = {
    val rawArray = sqlException.getMessage
      .split(s"\\($columnName_1, $columnName_2\\)=\\(")
      .drop(1)
      .head
      .takeWhile(_ != ')')
      .split(",")
      .map(_.trim)

    (UUID.fromString(rawArray.head), UUID.fromString(rawArray(1)))
  }

}
