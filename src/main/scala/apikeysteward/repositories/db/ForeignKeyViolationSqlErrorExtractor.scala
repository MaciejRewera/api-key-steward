package apikeysteward.repositories.db

import java.sql.SQLException

private[db] object ForeignKeyViolationSqlErrorExtractor {

  def extractColumnLongValue(sqlException: SQLException)(columnName: String): Long =
    sqlException.getMessage.split(s"\\($columnName\\)=\\(").apply(1).takeWhile(_.isDigit).toLong

  def extractTwoColumnsLongValues(
      sqlException: SQLException
  )(columnName_1: String, columnName_2: String): (Long, Long) = {
    val rawArray = sqlException.getMessage
      .split(s"\\($columnName_1, $columnName_2\\)=\\(")
      .drop(1)
      .head
      .takeWhile(_ != ')')
      .split(",")
      .map(_.trim)

    val (columnValue_1, columnValue_2) =
      (rawArray.head.takeWhile(_.isDigit).toLong, rawArray(1).takeWhile(_.isDigit).toLong)

    (columnValue_1, columnValue_2)
  }
}
