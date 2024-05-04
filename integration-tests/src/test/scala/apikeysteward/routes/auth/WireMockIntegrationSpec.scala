package apikeysteward.routes.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.http4s.Uri
import org.scalatest.{AsyncTestSuite, BeforeAndAfterAll, BeforeAndAfterEach}

import java.net.ServerSocket

trait WireMockIntegrationSpec extends BeforeAndAfterAll with BeforeAndAfterEach { self: AsyncTestSuite  =>

  protected lazy val wireMockPort: Int              = findAvailablePort()
  protected lazy val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort))
  protected lazy val wireMockUri: Uri               = Uri.unsafeFromString(s"http://localhost:$wireMockPort")

  private def findAvailablePort(): Int = {
    val ss   = new ServerSocket(0)
    val port = ss.getLocalPort
    ss.close()
    port
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    wireMockServer.start()
    WireMock.configureFor(wireMockPort)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()

    wireMockServer.resetAll()
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()

    super.afterAll()
  }
}
