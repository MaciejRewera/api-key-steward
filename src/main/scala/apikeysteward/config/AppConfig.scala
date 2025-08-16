package apikeysteward.config

import cats.Show
import com.comcast.ip4s.{Hostname, Port}
import io.circe.Encoder
import org.http4s.Uri
import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

case class AppConfig(
    http: HttpConfig,
    database: DatabaseConfig,
    features: FeaturesConfig,
    auth0Api: Option[Auth0ApiConfig],
    auth: AuthConfig,
    apiKey: ApiKeyConfig
)

object AppConfig {

  import io.circe.generic.auto._
  import io.circe.syntax._
  import pureconfig.generic.auto._
  import pureconfig.generic.semiauto.deriveReader
  import pureconfig.module.http4s._
  import pureconfig.module.ip4s._

  implicit val appConfigReader: ConfigReader[AppConfig]           = deriveReader[AppConfig]
  implicit val httpConfigReader: ConfigReader[HttpConfig]         = deriveReader[HttpConfig]
  implicit val databaseConfigReader: ConfigReader[DatabaseConfig] = deriveReader[DatabaseConfig]
  implicit val featuresConfigReader: ConfigReader[FeaturesConfig] = deriveReader[FeaturesConfig]
  implicit val auth0ApiConfigReader: ConfigReader[Auth0ApiConfig] = deriveReader[Auth0ApiConfig]
  implicit val authConfigReader: ConfigReader[AuthConfig]         = deriveReader[AuthConfig]

  // --------------------- SHOW ---------------------
  implicit val hostnameCirceEncoder: Encoder[Hostname] =
    Encoder.encodeString.contramap(_.toString)

  implicit val portCirceEncoder: Encoder[Port] =
    Encoder.encodeInt.contramap(_.value)

  implicit val uriCirceEncoder: Encoder[Uri] =
    Encoder.encodeString.contramap(_.renderString)

  implicit val finiteDurationCirceEncoder: Encoder[FiniteDuration] =
    Encoder.encodeString.contramap(_.toString)

  implicit val showAppConfig: Show[AppConfig] =
    _.asJson.toString()

}
