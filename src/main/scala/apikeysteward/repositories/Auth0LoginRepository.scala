package apikeysteward.repositories

import apikeysteward.connectors.Auth0LoginConnector
import apikeysteward.model.errors.Auth0Error.Auth0LoginError
import apikeysteward.repositories.Auth0LoginRepository.Auth0AccessToken
import apikeysteward.repositories.db.Auth0LoginDb
import apikeysteward.repositories.db.entity.Auth0LoginEntity
import apikeysteward.routes.auth.model.JwtCustom
import apikeysteward.services.UuidGenerator
import cats.data.EitherT
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._
import pdi.jwt.{JwtAlgorithm, JwtOptions}

class Auth0LoginRepository(
    uuidGenerator: UuidGenerator,
    auth0LoginDb: Auth0LoginDb,
    auth0LoginConnector: Auth0LoginConnector,
    jwtCustom: JwtCustom
)(transactor: Transactor[IO]) {

  def getAccessToken(tenantDomain: String): IO[Either[Auth0LoginError, Auth0AccessToken]] =
    for {
      auth0LoginOpt <- auth0LoginDb.getByTenantDomain(tenantDomain).transact(transactor)

      res <- auth0LoginOpt match {
        case Some(auth0Login) if hasAccessTokenNotExpiredYet(auth0Login.accessToken) =>
          IO.pure(Right(auth0Login.accessToken))

        case _ => fetchAndUpdateDb(tenantDomain)
      }
    } yield res

  // TODO: Would be better if this was executed only-and-exactly-once.
  private def fetchAndUpdateDb(tenantDomain: String): IO[Either[Auth0LoginError, Auth0AccessToken]] =
    (for {
      auth0LoginResponse <- auth0LoginConnector.fetchAccessToken(tenantDomain)

      id <- EitherT.right(uuidGenerator.generateUuid)
      loginEntityWrite = Auth0LoginEntity.Write.from(id, tenantDomain, auth0LoginResponse)

      loginEntityRead <- EitherT(auth0LoginDb.upsert(loginEntityWrite).transact(transactor))

    } yield loginEntityRead.accessToken).value

  private def hasAccessTokenNotExpiredYet(accessToken: Auth0AccessToken): Boolean = {
    val FakeKey                                        = "fakeKey"
    val DecodeAlgorithms: Seq[JwtAlgorithm.RS256.type] = Seq(JwtAlgorithm.RS256)
    val VerificationFlagsDecode: JwtOptions            = new JwtOptions(signature = false, expiration = true)

    val decodeResultE = jwtCustom
      .decodeAll(token = accessToken, key = FakeKey, algorithms = DecodeAlgorithms, options = VerificationFlagsDecode)
      .toEither

    decodeResultE match {
      case Right(_)  => true
      case Left(exc) => !exc.getMessage.toLowerCase.contains("the token is expired")
    }
  }

}

object Auth0LoginRepository {

  type Auth0AccessToken = String
}
