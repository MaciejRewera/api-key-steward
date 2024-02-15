package apikeysteward.repositories.db

import apikeysteward.repositories.DatabaseIntegrationSpec
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits.toSqlInterpolator
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.{Clock, Instant, ZoneOffset}

class ApiKeyDbSpec
  extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DatabaseIntegrationSpec {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"truncate api_key, api_key_data, api_key_scope, api_key_data_to_scope".update.run
  } yield ()

  private val now = Instant.parse("2023-09-13T12:34:56Z")
  implicit private def fixedClock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  private val apiKeyDb = new ApiKeyDb


  "ApiKeyDb on insert"  when {

    "there are no rows in the DB" should {

      "return inserted entity" in {

        IO({})
      }
    }
  }
}
