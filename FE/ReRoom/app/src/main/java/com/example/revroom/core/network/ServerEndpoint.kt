package com.example.revroom.core.network

import android.content.Context
import android.content.SharedPreferences
import com.example.revroom.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** A debug override; release always uses the endpoint packaged by the build. */
object ServerEndpoint {
    private val operationLock = Any()
    private var activeOperations = 0
    private var preferences: SharedPreferences? = null
    private val current = MutableStateFlow(BuildConfig.API_BASE_URL.toHttpUrl().toString())
    val baseUrl: StateFlow<String> = current.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("server_connection", Context.MODE_PRIVATE)
        preferences = prefs
        if (BuildConfig.DEBUG) {
            val saved = prefs.getString("base_url", null)
            current.value = saved?.let { runCatching { EndpointUrl.parse(it).toString() }.getOrNull() }
                ?: BuildConfig.API_BASE_URL.toHttpUrl().toString()
        }
    }

    fun save(value: String) = synchronized(operationLock) {
        check(BuildConfig.DEBUG) { "Server overrides are available in debug builds only." }
        check(activeOperations == 0) { "An upload or update is still running. Wait for it to finish, then try again." }
        val normalized = EndpointUrl.parse(value).toString()
        checkNotNull(preferences) { "Server settings have not been initialized." }
            .edit().putString("base_url", normalized).apply()
        current.value = normalized
    }

    /** Hold an endpoint stable across multi-request mutations, such as registration then upload. */
    suspend fun <T> withStableEndpoint(block: suspend () -> T): T {
        synchronized(operationLock) { activeOperations++ }
        try {
            return block()
        } finally {
            synchronized(operationLock) { activeOperations-- }
        }
    }
}

/** Kept independent of Android so malformed addresses and routing can be unit tested. */
object EndpointUrl {
    fun parse(value: String): HttpUrl {
        val input = value.trim()
        require(input.startsWith("http://", true) || input.startsWith("https://", true)) {
            "Enter a URL starting with http:// or https://."
        }
        val url = input.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server address. Check the host and port.")
        require(url.username.isEmpty() && url.password.isEmpty()) { "Do not include credentials in the URL." }
        require(url.query == null && url.fragment == null) { "Remove the query or fragment from the server URL." }
        require(url.host != "0.0.0.0" && url.host != "::") {
            "Use your computer's LAN address, not the server bind address."
        }
        require(url.encodedPath.trimEnd('/').substringAfterLast('/') != "api") {
            "Enter the server address without the /api suffix."
        }
        return if (url.encodedPath.endsWith('/')) url else url.newBuilder()
            .encodedPath(url.encodedPath + "/").build()
    }

    fun route(request: HttpUrl, original: HttpUrl, target: HttpUrl): HttpUrl {
        if (request.scheme != original.scheme || request.host != original.host ||
            request.port != original.port || !request.encodedPath.startsWith(original.encodedPath)) {
            return request
        }
        val relativePath = request.encodedPath.removePrefix(original.encodedPath)
        return target.newBuilder()
            .encodedPath(target.encodedPath + relativePath)
            .encodedQuery(request.encodedQuery)
            .build()
    }
}

/** Existing Retrofit services also pick up a new endpoint on their next request. */
class EndpointRoutingInterceptor(
    private val original: HttpUrl,
    private val target: () -> HttpUrl,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val routedUrl = EndpointUrl.route(request.url, original, target())
        return chain.proceed(request.newBuilder().url(routedUrl).build())
    }
}
