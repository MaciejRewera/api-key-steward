package apikeysteward.repositories.db

import apikeysteward.repositories.db.entity.ScopeEntity
import cats.data.NonEmptyList
import doobie.implicits._
import doobie.{Fragments, Update}
import fs2.Stream

class ScopeDb {

  def insertMany(scopes: List[ScopeEntity.Write]): Stream[doobie.ConnectionIO, ScopeEntity.Read] = {
    val result: doobie.ConnectionIO[List[ScopeEntity.Read]] = for {
      existingScopes <- get(scopes.map(_.scope)).compile.toList

      scopesToInsert = scopes.filterNot(s => existingScopes.exists(_.scope == s.scope))
      _ <- Queries.insertMany.updateMany(scopesToInsert)

      res <- get(scopes.map(_.scope)).compile.toList
    } yield res

    Stream.evals(result)
  }

  def get(scopes: List[String]): Stream[doobie.ConnectionIO, ScopeEntity.Read] =
    NonEmptyList
      .fromList(scopes)
      .fold[Stream[doobie.ConnectionIO, ScopeEntity.Read]](Stream.empty)(list => Queries.get(list).stream)

  private object Queries {

    val insertMany: doobie.Update[ScopeEntity.Write] = {
      val sql = "INSERT INTO scope (scope) VALUES (?)"
      Update[ScopeEntity.Write](sql)
    }

    def get(scopes: NonEmptyList[String]): doobie.Query0[ScopeEntity.Read] =
      (fr"SELECT id, scope FROM scope WHERE " ++ Fragments.in(fr"scope.scope", scopes)).query[ScopeEntity.Read]
  }

}
