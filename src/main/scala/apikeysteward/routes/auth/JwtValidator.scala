package apikeysteward.routes.auth

import apikeysteward.config.AuthConfig
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError
import apikeysteward.routes.auth.JwtValidator.JwtValidatorError._
import apikeysteward.routes.auth.model.{JsonWebToken, JwtClaimCustom}
import apikeysteward.utils.Logging
import cats.data.NonEmptyChain
import cats.implicits._
import pdi.jwt.JwtHeader

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class JwtValidator(authConfig: AuthConfig)(implicit clock: Clock) extends Logging {

  def validateKeyId(jwtHeader: JwtHeader): Either[JwtValidatorError, JwtHeader] =
    jwtHeader.keyId match {
      case None     => MissingKeyIdFieldError.asLeft
      case Some("") => MissingKeyIdFieldError.asLeft
      case Some(_)  => jwtHeader.asRight
    }

  def validateExpirationTimeClaim(jwtClaim: JwtClaimCustom): Either[JwtValidatorError, JwtClaimCustom] =
    jwtClaim.expiration match {
      case None if authConfig.requireExp => MissingExpirationTimeClaimError.asLeft
      case _                             => jwtClaim.asRight
    }

  def validateNotBeforeClaim(jwtClaim: JwtClaimCustom): Either[JwtValidatorError, JwtClaimCustom] =
    jwtClaim.notBefore match {
      case None if authConfig.requireNbf => MissingNotBeforeClaimError.asLeft
      case _                             => jwtClaim.asRight
    }

  def validateIssuedAtClaim(jwtClaim: JwtClaimCustom): Either[JwtValidatorError, JwtClaimCustom] =
    jwtClaim.issuedAt match {
      case None if authConfig.requireIat => MissingIssuedAtClaimError.asLeft
      case Some(issuedAtClaim)           => validateIssuedAtClaim(issuedAtClaim).map(_ => jwtClaim)
      case _                             => jwtClaim.asRight
    }

  private def validateIssuedAtClaim(issuedAtClaim: Long): Either[JwtValidatorError, Long] =
    authConfig.maxTokenAge match {
      case Some(maxTokenAge) if isYoungerThanMaxAllowed(issuedAtClaim, maxTokenAge) => issuedAtClaim.asRight
      case None                                                                     => issuedAtClaim.asRight

      case Some(maxTokenAge) => TokenTooOldError(maxTokenAge).asLeft
    }

  private def isYoungerThanMaxAllowed(issuedAtClaim: Long, maxTokenAge: FiniteDuration): Boolean = {
    val issuedAt = FiniteDuration(issuedAtClaim, TimeUnit.SECONDS)
    val nowInSeconds = FiniteDuration(Instant.now(clock).getEpochSecond, TimeUnit.SECONDS)

    issuedAt.plus(maxTokenAge) > nowInSeconds
  }

  def validateIssuerClaim(jwtClaim: JwtClaimCustom): Either[JwtValidatorError, JwtClaimCustom] =
    (jwtClaim.issuer, authConfig.requireIss) match {
      case (None, true)     => MissingIssuerClaimError.asLeft
      case (None, _)        => jwtClaim.asRight
      case (Some(""), true) => MissingIssuerClaimError.asLeft
      case (Some(""), _)    => jwtClaim.asRight

      case (Some(issuer), _) if authConfig.allowedIssuers.contains(issuer) => jwtClaim.asRight
      case (Some(issuer), _)                                               => IncorrectIssuerClaimError(issuer).asLeft
    }

  def validateAudienceClaim(jwtClaim: JwtClaimCustom): Either[JwtValidatorError, JwtClaimCustom] =
    jwtClaim.audience match {
      case None if authConfig.requireAud => MissingAudienceClaimError.asLeft
      case None                          => jwtClaim.asRight

      case Some(audienceSet) => validateAudienceClaim(audienceSet).map(_ => jwtClaim)
    }

  private def validateAudienceClaim(audienceSet: Set[String]): Either[JwtValidatorError, Set[String]] = {
    val filteredAudienceSet = audienceSet.filter(_.nonEmpty)

    (filteredAudienceSet, filteredAudienceSet.isEmpty) match {
      case (_, true) if authConfig.requireAud => MissingAudienceClaimError.asLeft
      case (_, true)                          => audienceSet.asRight

      case (filteredAudienceSet, _) if filteredAudienceSet(authConfig.audience) => audienceSet.asRight
      case (filteredAudienceSet, _)                                             => IncorrectAudienceClaimError(filteredAudienceSet).asLeft
    }
  }

  def validateAll(token: JsonWebToken): Either[NonEmptyChain[JwtValidatorError], JsonWebToken] =
    (
      validateKeyId(token.header).toValidatedNec,
      validateExpirationTimeClaim(token.claim).toValidatedNec,
      validateNotBeforeClaim(token.claim).toValidatedNec,
      validateIssuedAtClaim(token.claim).toValidatedNec,
      validateIssuerClaim(token.claim).toValidatedNec,
      validateAudienceClaim(token.claim).toValidatedNec
    ).mapN((_, _, _, _, _, _) => token).toEither

}

object JwtValidator {

  sealed abstract class JwtValidatorError(val message: String)
  object JwtValidatorError {

    case object MissingKeyIdFieldError extends JwtValidatorError("Key ID (kid) claim is missing.")

    case object MissingExpirationTimeClaimError extends JwtValidatorError("Expiration time (exp) claim is missing.")

    case object MissingNotBeforeClaimError extends JwtValidatorError("Not before (nbf) claim is missing.")

    case object MissingIssuerClaimError extends JwtValidatorError("Issuer (iss) claim is missing.")

    case class IncorrectIssuerClaimError(issuer: String)
        extends JwtValidatorError(s"Issuer (iss): '$issuer' is not supported.")

    case object MissingAudienceClaimError extends JwtValidatorError("Audience (aud) claim is missing.")

    case class IncorrectAudienceClaimError(audience: Set[String])
        extends JwtValidatorError(s"Audience (aud) claim is incorrect: ${audience.mkString("[", ", ", "]")}.")

    case object MissingIssuedAtClaimError extends JwtValidatorError("Issued at (iat) claim is missing.")

    case class TokenTooOldError(maxTokenAge: FiniteDuration)
        extends JwtValidatorError(s"The provided JWT is older than maximum allowed age of: ${maxTokenAge.toString}")

  }
}
