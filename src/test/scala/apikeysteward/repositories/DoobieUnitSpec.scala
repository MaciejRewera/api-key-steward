package apikeysteward.repositories

import cats.effect.IO
import doobie.util.transactor.{Strategy, Transactor}

trait DoobieUnitSpec {

  val noopTransactor: Transactor[IO] = Transactor.fromConnection[IO](null).copy(strategy0 = Strategy.void)
}
