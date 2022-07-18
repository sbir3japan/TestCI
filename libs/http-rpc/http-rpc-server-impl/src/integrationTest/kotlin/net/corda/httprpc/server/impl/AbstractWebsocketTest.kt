package net.corda.httprpc.server.impl

import io.javalin.core.util.Header
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.tools.HttpVerb
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.websocket.api.CloseStatus
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.UpgradeRequest
import org.eclipse.jetty.websocket.api.UpgradeResponse
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.NoOpEndpoint
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.client.io.UpgradeListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.net.URI
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractWebsocketTest : HttpRpcServerTestBase() {

    protected abstract fun createWsClient(): WebSocketClient

    protected abstract val wsProtocol: String

    protected abstract val log: Logger

    protected abstract val httpRpcSettings: HttpRpcSettings

    @AfterEach
    fun reset() {
        securityManager.forgetChecks()
    }

    @Test
    fun `valid path returns 200 OK`() {

        val getPathResponse = client.call(HttpVerb.GET, WebRequest<Any>("health/sanity"), userName, password)
        assertEquals(HttpStatus.SC_OK, getPathResponse.responseStatus)
        assertEquals("localhost", getPathResponse.headers[Header.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertEquals("true", getPathResponse.headers[Header.ACCESS_CONTROL_ALLOW_CREDENTIALS])
        assertEquals("no-cache", getPathResponse.headers[Header.CACHE_CONTROL])
    }

    @Test
    fun `check WebSocket interaction`() {
        val wsClient = createWsClient()
        wsClient.start()

        val closeLatch = CountDownLatch(1)

        val maximumDesiredCount = 100
        val list = mutableListOf<String>()

        val wsHandler = object : NoOpEndpoint() {

            override fun onWebSocketConnect(session: Session) {
                log.info("onWebSocketConnect : $session")
            }

            override fun onWebSocketClose(statusCode: Int, reason: String?) {
                log.info("Reacting to server closed: $statusCode - $reason")
                closeLatch.countDown()
                super.onWebSocketClose(statusCode, reason)
            }

            override fun onWebSocketText(message: String) {
                list.add(message)
                if (list.size >= maximumDesiredCount) {
                    log.warn("Too many received!")
                    closeLatch.countDown()
                }
            }
        }

        val upgradeListener = object: UpgradeListener {
            override fun onHandshakeRequest(request: UpgradeRequest) {
                val headerValue = toBasicAuthValue(userName, password)
                log.info("Header value: $headerValue")
                request.setHeader(Header.AUTHORIZATION, headerValue)
            }

            override fun onHandshakeResponse(response: UpgradeResponse) {
            }
        }

        val start = 100
        val range = 50

        val uri = URI(
            "$wsProtocol://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/" +
                    "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/health/counterfeed/$start?range=$range"
        )

        log.info("Connecting to: $uri")

        val session = wsClient.connect(wsHandler, uri, ClientUpgradeRequest(), upgradeListener)
            .get(10, TimeUnit.SECONDS)
        log.info("Session established: $session")
        assertTrue(closeLatch.await(10, TimeUnit.SECONDS))
        session.close(CloseStatus(StatusCode.NORMAL, "Gracefully closing from client side."))
        wsClient.stop()

        val expectedContent = (start until start + range).map { "$it" }
        assertThat(list).isEqualTo(expectedContent)

        assertThat(securityManager.checksExecuted).hasSize(1)
            .allMatch { it.action == "WS:/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/" +
                    "health/counterfeed/{start}?range=$range" }
    }

    private fun toBasicAuthValue(username: String, password: String): String {
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }

    @Test
    fun `check WebSocket wrong credentials connectivity`() {
        val upgradeListener = object: UpgradeListener {
            override fun onHandshakeRequest(request: UpgradeRequest) {
                request.setHeader(Header.AUTHORIZATION, toBasicAuthValue("alienUser", "wrongPassword"))
            }

            override fun onHandshakeResponse(response: UpgradeResponse) {
            }
        }

        performUnauthorizedTest(upgradeListener)
    }

    @Test
    fun `check WebSocket no credentials connectivity`() {

        performUnauthorizedTest(null)
    }

    private fun performUnauthorizedTest(upgradeListener: UpgradeListener?) {
        val wsClient = createWsClient()
        wsClient.start()

        val latch = CountDownLatch(2)
        var closeStatus: CloseStatus? = null

        val wsHandler = object : NoOpEndpoint() {

            override fun onWebSocketConnect(session: Session) {
                log.info("onWebSocketConnect : $session")
                latch.countDown()
            }

            override fun onWebSocketClose(statusCode: Int, reason: String?) {
                super.onWebSocketClose(statusCode, reason)
                closeStatus = CloseStatus(statusCode, reason)
                latch.countDown()

            }

            override fun onWebSocketError(cause: Throwable?) {
                log.info("onWebSocketError : $session", cause)
            }
        }

        val uri = URI(
            "$wsProtocol://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/" +
                    "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/health/counterfeed/100"
        )

        log.info("Connecting to: $uri")

        val session = wsClient.connect(wsHandler, uri, ClientUpgradeRequest(), upgradeListener)
            .get(10, TimeUnit.SECONDS)
        log.info("Session established: $session")

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        wsClient.stop()

        assertThat(closeStatus?.code).isEqualTo(StatusCode.POLICY_VIOLATION)
    }
}