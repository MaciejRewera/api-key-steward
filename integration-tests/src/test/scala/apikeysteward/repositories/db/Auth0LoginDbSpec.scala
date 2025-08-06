package apikeysteward.repositories.db

import apikeysteward.base.FixedClock
import apikeysteward.base.testdata.Auth0LoginTestData._
import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.{ApiKeyTemplateEntity, Auth0LoginEntity}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.none
import doobie.ConnectionIO
import doobie.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class Auth0LoginDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with FixedClock
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE auth0_login_token CASCADE".update.run
  } yield ()

  private val auth0LoginDb = new Auth0LoginDb

  private object Queries extends DoobieCustomMeta {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllAuth0Logins: doobie.ConnectionIO[List[Auth0LoginEntity.Read]] =
      sql"SELECT * FROM auth0_login_token".query[Auth0LoginEntity.Read].stream.compile.toList

  }

  "Auth0LoginDb on upsert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = (for {
          res <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(auth0LoginEntityRead_1))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)

          res <- Queries.getAllAuth0Logins
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(auth0LoginEntityRead_1))
      }
    }

    "there is a row in the DB with a different audience" should {

      "return inserted entity" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)

          res <- auth0LoginDb.upsert(auth0LoginEntityWrite_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(auth0LoginEntityRead_2))
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_2)

          res <- Queries.getAllAuth0Logins
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(auth0LoginEntityRead_1, auth0LoginEntityRead_2))
      }
    }

    "there is a row in the DB with the same audience" should {

      "return upsert-ed entity" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)

          res <- auth0LoginDb.upsert(auth0LoginEntityWrite_2.copy(audience = audience_1))
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Right(auth0LoginEntityRead_2.copy(id = auth0LoginDbId_1, audience = audience_1)))
      }

      "upsert entity into DB" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_2.copy(audience = audience_1))

          res <- Queries.getAllAuth0Logins
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List(auth0LoginEntityRead_2.copy(id = auth0LoginDbId_1, audience = audience_1)))
      }
    }
  }

  "Auth0LoginDb on getByAudience" when {

    "there are no rows in the DB" should {
      "return empty Option" in {
        val result = (for {
          res <- auth0LoginDb.getByAudience(audience_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[Auth0LoginEntity.Read])
      }
    }

    "there is a row in the DB with different audience" should {
      "return empty Option" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)

          res <- auth0LoginDb.getByAudience(audience_2)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe none[Auth0LoginEntity.Read])
      }
    }

    "there is a row in the DB with the same audience" should {
      "return this entity" in {
        val result = (for {
          _ <- auth0LoginDb.upsert(auth0LoginEntityWrite_1)

          res <- auth0LoginDb.getByAudience(audience_1)
        } yield res).transact(transactor)

        result.asserting(_ shouldBe Some(auth0LoginEntityRead_1))
      }
    }
  }

}
