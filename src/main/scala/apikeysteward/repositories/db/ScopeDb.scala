package apikeysteward.repositories.db

import apikeysteward.repositories.db.entity.ScopeEntity
import cats.data.NonEmptyList
import doobie.implicits._
import doobie.{Fragments, Update}
import fs2.Stream

class ScopeDb {

  def insertMany(scopeEntities: List[ScopeEntity.Write]): Stream[doobie.ConnectionIO, ScopeEntity.Read] = {
    val result: doobie.ConnectionIO[List[ScopeEntity.Read]] = for {
      existingScopes <- get(scopeEntities.map(_.scope)).compile.toList

      scopesToInsert = scopeEntities.filterNot(s => existingScopes.exists(_.scope == s.scope))
      _ <- Queries.insertMany.updateMany(scopesToInsert)

      res <- get(scopeEntities.map(_.scope)).compile.toList
    } yield res

    Stream.evals(result)
  }

  def get(scopes: List[String]): Stream[doobie.ConnectionIO, ScopeEntity.Read] =
    getBy(scopes)(Queries.get)

  def getByIds(scopeIds: List[Long]): Stream[doobie.ConnectionIO, ScopeEntity.Read] =
    getBy(scopeIds)(Queries.getByIds)

  private def getBy[A, B](list: List[A])(query: NonEmptyList[A] => doobie.Query0[B]): Stream[doobie.ConnectionIO, B] =
    NonEmptyList
      .fromList(list)
      .fold[Stream[doobie.ConnectionIO, B]](Stream.empty)(l => query(l).stream)

  private object Queries {

    val insertMany: doobie.Update[ScopeEntity.Write] = {
      val sql = "INSERT INTO scope (scope) VALUES (?)"
      Update[ScopeEntity.Write](sql)
    }

    def get(scopes: NonEmptyList[String]): doobie.Query0[ScopeEntity.Read] =
      (fr"SELECT id, scope FROM scope WHERE " ++ Fragments.in(fr"scope.scope", scopes)).query[ScopeEntity.Read]

    def getByIds(scopeIds: NonEmptyList[Long]): doobie.Query0[ScopeEntity.Read] =
      (fr"SELECT id, scope FROM scope WHERE " ++ Fragments.in(fr"scope.id", scopeIds)).query[ScopeEntity.Read]
  }

}
