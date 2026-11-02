package com.example.revroom.core.network

import com.google.gson.JsonParser
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object ConnectionCheck {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun test(value: String): String = withContext(Dispatchers.IO) {
        val endpoint = EndpointUrl.parse(value)
        val request = Request.Builder().url(endpoint.resolve("api/webhook/health")!!).get().build()
        val started = System.nanoTime()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Health check returned HTTP ${response.code}. Check the API address.")
            }
            // A wrong URL may return an HTML page; only read a bounded health response.
            val text = response.peekBody(16_384).string()
            val healthy = runCatching {
                JsonParser.parseString(text).asJsonObject.get("status")?.asString == "healthy"
            }.getOrDefault(false)
            if (!healthy) throw IOException("This address did not return a Home AI health response.")
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            "API reachable (${elapsedMs} ms). This checks connectivity, not database or AI availability."
        }
    }

    fun errorMessage(error: Throwable): String = when (error) {
        is UnknownHostException -> "Cannot resolve the host. Check the address and network connection."
        is ConnectException -> "Cannot connect. Check that the backend is running, the IP/port is correct, and the firewall allows it."
        is SocketTimeoutException -> "Connection timed out. Check the network, server address and firewall."
        is SSLException -> "HTTPS certificate could not be verified. Use a server with a valid certificate."
        else -> error.message ?: "Unable to reach the server."
    }
}
