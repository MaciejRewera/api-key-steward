package apikeysteward.repositories.db

import apikeysteward.repositories.DatabaseIntegrationSpec
import apikeysteward.repositories.db.entity.ScopeEntity
import cats.effect.testing.scalatest.AsyncIOSpec
import doobie.ConnectionIO
import doobie.implicits.{toDoobieStreamOps, toSqlInterpolator}
import fs2.Stream
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import apikeysteward.base.IntegrationTestData._

class ScopeDbSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with DatabaseIntegrationSpec with EitherValues {

  override protected val resetDataQuery: ConnectionIO[_] = for {
    _ <- sql"TRUNCATE scope CASCADE".update.run
  } yield ()

  private val scopeDb = new ScopeDb

  private object Queries {

    import doobie.postgres._
    import doobie.postgres.implicits._

    val getAllScopes: Stream[doobie.ConnectionIO, ScopeEntity.Read] =
      sql"SELECT * FROM scope".query[ScopeEntity.Read].stream
  }

  "ScopeDb on insertMany" when {

    "provided with NO scopes" should {

      val inputScopes = List.empty[ScopeEntity.Write]

      "return empty Stream" in {
        scopeDb.insertMany(inputScopes).transact(transactor).compile.toList.asserting(_ shouldBe empty)
      }

      "NOT insert anything into DB" in {
        val result = for {
          _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

          res <- Queries.getAllScopes.transact(transactor).compile.toList
        } yield res

        result.asserting(_ shouldBe empty)
      }
    }

    "provided with a single scope" when {

      val inputScopes = List(scopeRead_1).map(ScopeEntity.Write)

      "there are no rows in the DB" should {

        "return inserted entity" in {
          scopeDb.insertMany(inputScopes).transact(transactor).compile.toList.asserting { res =>
            res.size shouldBe 1
            res.head.scope shouldBe scopeRead_1
          }
        }

        "insert entity into DB" in {
          val result = for {
            _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 1
            res.head.scope shouldBe scopeRead_1
          }
        }
      }

      "there is a row in the DB with the same scope" should {

        "return the 'old' entity" in {
          val result = for {
            oldEntities <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList
          } yield (oldEntities, res)

          result.asserting { case (oldEntities, res) =>
            oldEntities.size shouldBe 1
            res.size shouldBe 1

            res.head shouldBe oldEntities.head
          }
        }

        "NOT insert the second entity into DB" in {
          val result = for {
            oldEntities <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList
            _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield (oldEntities, res)

          result.asserting { case (oldEntities, res) =>
            oldEntities.size shouldBe 1
            res.size shouldBe 1

            res.head shouldBe oldEntities.head
          }
        }
      }

      "there is a row in the DB with a different scope" should {

        val oldScopes = List(scopeRead_2).map(ScopeEntity.Write)

        "return inserted entity" in {
          val result = for {
            _ <- scopeDb.insertMany(oldScopes).transact(transactor).compile.toList

            res <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting { results =>
            results.size shouldBe 1
            results.head.scope shouldBe scopeRead_1
          }
        }

        "insert entity into DB" in {
          val result = for {
            _ <- scopeDb.insertMany(oldScopes).transact(transactor).compile.toList
            _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield res

          result.asserting { results =>
            results.size shouldBe 2
            results.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeRead_2)
          }
        }
      }
    }

    "provided with multiple scopes" when {

      val inputScopes = List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2).map(ScopeEntity.Write)

      "there are no rows in the DB" should {

        "return inserted entities" in {
          scopeDb.insertMany(inputScopes).transact(transactor).compile.toList.asserting { res =>
            res.size shouldBe 4
            res.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2)
          }
        }

        "insert entities into DB" in {
          val result = for {
            _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 4
            res.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2)
          }
        }
      }

      "there are rows in the DB with the same scopes as input" should {

        val oldScopes = List(scopeRead_1, scopeWrite_1).map(ScopeEntity.Write)

        "return inserted entities, no matter if they are duplicated or not" in {
          val result = for {
            oldEntities <- scopeDb.insertMany(oldScopes).transact(transactor).compile.toList

            res <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList
          } yield (oldEntities, res)

          result.asserting { case (oldEntities, res) =>
            oldEntities.size shouldBe 2
            res.size shouldBe 4

            res should contain allElementsOf oldEntities
            res.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2)
          }
        }

        "NOT insert duplicating entities into DB" in {
          val result = for {
            oldEntities <- scopeDb.insertMany(oldScopes).transact(transactor).compile.toList
            _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield (oldEntities, res)

          result.asserting { case (oldEntities, res) =>
            oldEntities.size shouldBe 2
            res.size shouldBe 4

            res should contain allElementsOf oldEntities
            res.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2)
          }
        }
      }

      "there are rows in the DB with different scopes" should {

        val oldScopes = List(scopeRead_3, scopeWrite_3).map(ScopeEntity.Write)

        "return inserted entities" in {
          val result = for {
            oldEntities <- scopeDb.insertMany(oldScopes).transact(transactor).compile.toList

            res <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList
          } yield (oldEntities, res)

          result.asserting { case (oldEntities, res) =>
            oldEntities.size shouldBe 2
            res.size shouldBe 4

            res should contain noElementsOf oldEntities
            res.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2)
          }
        }

        "insert entities into DB" in {
          val result = for {
            oldEntities <- scopeDb.insertMany(oldScopes).transact(transactor).compile.toList
            _ <- scopeDb.insertMany(inputScopes).transact(transactor).compile.toList

            res <- Queries.getAllScopes.transact(transactor).compile.toList
          } yield (oldEntities, res)

          result.asserting { case (oldEntities, res) =>
            oldEntities.size shouldBe 2
            res.size shouldBe 6

            res should contain allElementsOf oldEntities
            res.map(_.scope) should contain theSameElementsAs
              List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2, scopeRead_3, scopeWrite_3)
          }
        }
      }
    }
  }

  "ScopeDb on get(:scopes)" when {

    "provided with a single scope" when {

      val inputScopes = List(scopeRead_1)

      "there are no rows in the DB" should {
        "return empty Stream" in {
          scopeDb.get(inputScopes).transact(transactor).compile.toList.asserting(_ shouldBe empty)
        }
      }

      "there is a row in the DB with different scope" should {
        "return empty Stream" in {
          val result = for {
            _ <- scopeDb.insertMany(List(scopeRead_2).map(ScopeEntity.Write)).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting(_ shouldBe empty)
        }
      }

      "there is a row in the DB with the same scope" should {
        "return Stream containing ScopeEntity" in {
          val result = for {
            _ <- scopeDb.insertMany(inputScopes.map(ScopeEntity.Write)).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 1
            res.head.scope shouldBe scopeRead_1
          }
        }
      }

      "there are multiple rows in the DB and one is with the same scope" should {
        "return Stream containing ScopeEntity" in {
          val oldEntities = (inputScopes ++ List(scopeWrite_1, scopeRead_2, scopeWrite_2)).map(ScopeEntity.Write)
          val result = for {
            _ <- scopeDb.insertMany(oldEntities).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 1
            res.head.scope shouldBe scopeRead_1
          }
        }
      }
    }

    "provided with multiple scopes" when {

      val inputScopes = List(scopeRead_1, scopeWrite_1, scopeRead_2, scopeWrite_2)

      "there are no rows in the DB" should {
        "return empty Stream" in {
          scopeDb.get(inputScopes).transact(transactor).compile.toList.asserting(_ shouldBe empty)
        }
      }

      "there are rows in the DB with different scopes" should {
        "return empty Stream" in {
          val oldEntities = List(scopeRead_3, scopeWrite_3).map(ScopeEntity.Write)
          val result = for {
            _ <- scopeDb.insertMany(oldEntities).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting(_ shouldBe empty)
        }
      }

      "there is a single row in the DB with matching scope" should {
        "return Stream containing single ScopeEntity" in {
          val oldEntities = List(scopeRead_1).map(ScopeEntity.Write)
          val result = for {
            _ <- scopeDb.insertMany(oldEntities).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 1
            res.head.scope shouldBe scopeRead_1
          }
        }
      }

      "there are multiple rows in the DB with matching scopes" should {
        "return Stream containing multiple ScopeEntities" in {
          val oldEntities = inputScopes.map(ScopeEntity.Write)
          val result = for {
            _ <- scopeDb.insertMany(oldEntities).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 4
            res.map(_.scope) should contain theSameElementsAs inputScopes
          }
        }
      }

      "there are multiple rows in the DB and some of them with matching scopes" should {
        "return Stream containing ScopeEntity" in {
          val oldEntities =
            List(scopeRead_1, scopeRead_2, scopeWrite_2, scopeRead_3, scopeWrite_3).map(ScopeEntity.Write)
          val result = for {
            _ <- scopeDb.insertMany(oldEntities).transact(transactor).compile.toList

            res <- scopeDb.get(inputScopes).transact(transactor).compile.toList
          } yield res

          result.asserting { res =>
            res.size shouldBe 3
            res.map(_.scope) should contain theSameElementsAs List(scopeRead_1, scopeRead_2, scopeWrite_2)
          }
        }
      }
    }
  }

}
