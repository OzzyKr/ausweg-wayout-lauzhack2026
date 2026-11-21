package ch.ausweg.server.ojp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException

const val OJP_URL = "https://api.opentransportdata.swiss/ojp20"

private const val OJP_TIMEOUT_MS = 8_000L

/** OJP couldn't answer: no key, network error, HTTP error, or an unreadable response. */
class OjpUnavailable(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The OJP API key. Its [toString] hides the value so it can't end up in a log line by accident. */
class OjpKey(
    val value: String,
) {
    override fun toString() = "OjpKey(***)"
}

/** Sends one OJP request body and returns the response body. Faked in tests. */
fun interface OjpTransport {
    /** @throws OjpUnavailable on any failure. */
    suspend fun post(xml: String): String
}

/** The real OJP 2.0 endpoint, with the key as a Bearer token. */
class HttpOjpTransport(
    private val key: OjpKey,
    private val client: HttpClient = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = OJP_TIMEOUT_MS } },
) : OjpTransport {
    override suspend fun post(xml: String): String {
        val response =
            try {
                client.post(OJP_URL) {
                    header(HttpHeaders.Authorization, "Bearer ${key.value}")
                    contentType(ContentType.Application.Xml)
                    setBody(xml)
                }
            } catch (e: IOException) {
                throw OjpUnavailable("OJP unreachable: ${e.javaClass.simpleName}", e)
            } catch (e: IllegalArgumentException) {
                // e.g. UnresolvedAddressException (no network).
                throw OjpUnavailable("OJP unreachable: ${e.javaClass.simpleName}", e)
            }
        if (!response.status.isSuccess()) throw OjpUnavailable("OJP answered HTTP ${response.status.value}")
        return response.bodyAsText()
    }
}
