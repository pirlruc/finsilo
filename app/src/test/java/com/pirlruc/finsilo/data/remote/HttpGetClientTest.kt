package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpGetClientTest {
    @Test
    fun clientDoesNotFollowRedirects() {
        val client = marketHttpClient()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertTrue(client.networkInterceptors.any { it is GetOnlyInterceptor })
    }

    @Test(expected = IOException::class)
    fun networkInterceptorRefusesNonAllowlistedHost() {
        GetOnlyInterceptor().intercept(FakeChain(Request.Builder().url("https://evil.example/steal").get().build()))
    }

    @Test
    fun networkInterceptorAllowsListedHostWithoutRedirectHop() {
        val request = Request.Builder().url("https://stooq.com/q/d/l/?s=aapl.us").get().build()
        val chain = FakeChain(request, proceed = true)
        val response = GetOnlyInterceptor().intercept(chain)
        assertTrue(response.isSuccessful)
        assertTrue(chain.proceeded)
    }

    private class FakeChain(private val request: Request, private val proceed: Boolean = false) : Interceptor.Chain {
        var proceeded: Boolean = false

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceeded = true
            if (!proceed) error("must not proceed")
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("ok".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        override fun connection() = null

        override fun call() = error("unused")

        override fun connectTimeoutMillis() = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this

        override fun readTimeoutMillis() = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this

        override fun writeTimeoutMillis() = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
}
