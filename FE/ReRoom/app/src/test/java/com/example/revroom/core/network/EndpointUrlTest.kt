package com.example.revroom.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointUrlTest {
    @Test fun normalizesBaseUrlAndKeepsReverseProxyPrefix() {
        assertEquals("http://192.168.1.2:5207/", EndpointUrl.parse(" http://192.168.1.2:5207 ").toString())
        assertEquals("https://host.test/home-ai/", EndpointUrl.parse("https://host.test/home-ai").toString())
    }

    @Test fun rejectsInvalidOrMisleadingServerAddresses() {
        listOf("192.168.1.2:5207", "ftp://host.test", "http://0.0.0.0:5207", "http://[::]:5207",
            "https://user:password@host.test/", "https://host.test/?key=x", "https://host.test/#fragment",
            "https://host.test/api/").forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { EndpointUrl.parse(value) }
        }
    }

    @Test fun existingClientUsesNewEndpointAndPreservesEncodedQuery() {
        val source = "http://10.0.2.2:5207/".toHttpUrl()
        var target = EndpointUrl.parse("http://192.168.1.2:5207/")
        val seen = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(EndpointRoutingInterceptor(source) { target })
            .addInterceptor { chain ->
                seen.add(chain.request().url.toString())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("{}".toResponseBody("application/json".toMediaType())).build()
            }.build()
        val request = Request.Builder()
            .url("http://10.0.2.2:5207/api/design/projects?page=2&name=a%2Fb").build()

        client.newCall(request).execute().close()
        target = EndpointUrl.parse("https://demo.test/home-ai/")
        client.newCall(request).execute().close()

        assertEquals(listOf(
            "http://192.168.1.2:5207/api/design/projects?page=2&name=a%2Fb",
            "https://demo.test/home-ai/api/design/projects?page=2&name=a%2Fb",
        ), seen)
    }

    @Test fun doesNotRewriteUnrelatedAbsoluteRequests() {
        val external = "https://images.test/photo.jpg".toHttpUrl()
        assertEquals(external, EndpointUrl.route(external, "http://10.0.2.2:5207/".toHttpUrl(),
            "https://demo.test/".toHttpUrl()))
    }
}
