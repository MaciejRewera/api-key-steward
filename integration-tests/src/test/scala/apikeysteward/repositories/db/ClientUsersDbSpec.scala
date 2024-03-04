package apikeysteward.repositories.db

import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ClientUsersEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.sql.SQLException
import java.time.{Clock, Instant, ZoneOffset}

class ClientUsersDbSpec
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with DatabaseIntegrationSpec
    with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE client_users".update.run
  } yield ()

  private val now = Instant.parse("2024-02-15T12:34:56Z")
  implicit private def fixedClock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  private val clientUsersDb = new ClientUsersDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAll: doobie.ConnectionIO[List[ClientUsersEntity.Read]] =
      sql"SELECT * FROM client_users".query[ClientUsersEntity.Read].stream.compile.toList
  }

  private val testClientId_1 = "test-client-001"
  private val testClientId_2 = "test-client-002"
  private val testClientId_3 = "test-client-003"

  private val testUserId_1 = "test-user-001"
  private val testUserId_2 = "test-user-002"
  private val testUserId_3 = "test-user-003"

  "ClientUsersDb on insert" when {

    "there are no rows in the DB" should {

      "return inserted entity" in {
        val result = clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1)).transact(transactor)

        result.asserting { res =>
          res shouldBe ClientUsersEntity.Read(id = res.id, testClientId_1, testUserId_1, now, now)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))

          res <- Queries.getAll
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe ClientUsersEntity.Read(id = res.head.id, testClientId_1, testUserId_1, now, now)
        }
      }
    }

    "there is a row in the DB with the same clientID" should {

      "return inserted entity" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_2))

          res <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe ClientUsersEntity.Read(id = res.id, testClientId_1, testUserId_1, now, now)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_2))

          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))
          res <- Queries.getAll
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 2
          res.head shouldBe ClientUsersEntity.Read(id = res.head.id, testClientId_1, testUserId_2, now, now)
          res(1) shouldBe ClientUsersEntity.Read(id = res(1).id, testClientId_1, testUserId_1, now, now)
        }
      }
    }

    "there is a row in the DB with the same userId" should {

      "return inserted entity" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_2, testUserId_1))

          res <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))
        } yield res).transact(transactor)

        result.asserting { res =>
          res shouldBe ClientUsersEntity.Read(id = res.id, testClientId_1, testUserId_1, now, now)
        }
      }

      "insert entity into DB" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_2, testUserId_1))

          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))
          res <- Queries.getAll
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 2
          res.head shouldBe ClientUsersEntity.Read(id = res.head.id, testClientId_2, testUserId_1, now, now)
          res(1) shouldBe ClientUsersEntity.Read(id = res(1).id, testClientId_1, testUserId_1, now, now)
        }
      }
    }

    "there is a row in the DB with the same both clientId and userId" should {

      "return error" in {
        val result = for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1)).transact(transactor)
          res <- clientUsersDb
            .insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))
            .transact(transactor)
            .attempt
        } yield res

        result.asserting { res =>
          res.isLeft shouldBe true

          res.left.value shouldBe an[SQLException]
          res.left.value.asInstanceOf[SQLException].getSQLState shouldBe UNIQUE_VIOLATION.value
          res.left.value.getMessage should include("ERROR: duplicate key value violates unique constraint")
        }
      }

      "NOT insert the new entity into the DB" in {
        val result = for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1)).transact(transactor)
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1)).transact(transactor).attempt

          res <- Queries.getAll.transact(transactor)
        } yield res

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe ClientUsersEntity.Read(id = res.head.id, testClientId_1, testUserId_1, now, now)
        }
      }
    }
  }

  "ClientUsersDb on getAllByClientId" when {

    "there are no rows in the DB" should {
      "return empty List" in {
        val result = clientUsersDb.getAllByClientId(testClientId_1).compile.toList.transact(transactor)

        result.asserting(_ shouldBe List.empty[ClientUsersEntity.Read])
      }
    }

    "there is a row in the DB with different clientId" should {
      "return empty List" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))

          res <- clientUsersDb.getAllByClientId(testClientId_2).compile.toList
        } yield res).transact(transactor)

        result.asserting(_ shouldBe List.empty[ClientUsersEntity.Read])
      }
    }

    "there is a row in the DB with the same clientId" should {
      "return Stream containing ClientUsersEntity" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))

          res <- clientUsersDb.getAllByClientId(testClientId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 1
          res.head shouldBe ClientUsersEntity.Read(res.head.id, testClientId_1, testUserId_1, now, now)
        }
      }
    }

    "there are several rows in the DB with the same clientId together with rows with different clientIds" should {
      "return Stream containing all matching ClientUsersEntity" in {
        val result = (for {
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_1))
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_2))
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_1, testUserId_3))

          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_2, testUserId_2))
          _ <- clientUsersDb.insert(ClientUsersEntity.Write(testClientId_3, testUserId_3))

          res <- clientUsersDb.getAllByClientId(testClientId_1).compile.toList
        } yield res).transact(transactor)

        result.asserting { res =>
          res.size shouldBe 3
          res.head shouldBe ClientUsersEntity.Read(res.head.id, testClientId_1, testUserId_1, now, now)
          res(1) shouldBe ClientUsersEntity.Read(res(1).id, testClientId_1, testUserId_2, now, now)
          res(2) shouldBe ClientUsersEntity.Read(res(2).id, testClientId_1, testUserId_3, now, now)
        }
      }
    }
  }
}
