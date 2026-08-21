package com.pirlruc.finsilo.data.remote

import java.io.IOException
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
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
        validateMarketGet(Request.Builder().url("https://evil.example/steal").get().build())
    }

    @Test
    fun networkInterceptorAllowsListedHostWithoutRedirectHop() {
        validateMarketGet(
            Request.Builder().url("https://stooq.com/q/d/l/?s=aapl.us").get().build(),
        )
    }

    @Test(expected = IOException::class)
    fun networkInterceptorRefusesNonGet() {
        validateMarketGet(
            Request.Builder().url("https://stooq.com/").post(ByteArray(0).toRequestBody()).build(),
        )
    }

    @Test
    fun utf8BodyIsCapped() {
        val source = Buffer().writeUtf8("ok")
        assertEquals("ok", readUtf8Capped(source, 16))
        try {
            readUtf8Capped(Buffer().writeUtf8("too-big-payload"), 4)
            error("expected cap")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("too large"))
        }
    }
}
