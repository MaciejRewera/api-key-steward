package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.{ApplicationEntity, TenantEntity}
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits.toSqlInterpolator
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ApplicationDbSpec  extends AsyncWordSpec
  with AsyncIOSpec
  with Matchers
  with FixedClock
  with DatabaseIntegrationSpec
  with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE tenant, application CASCADE".update.run
  } yield ()

  private val applicationDb = new ApplicationDb

  private object Queries {
    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllApplications: doobie.ConnectionIO[List[ApplicationEntity.Read]] =
      sql"SELECT * FROM application".query[ApplicationEntity.Read].stream.compile.toList
  }

  ""

}
