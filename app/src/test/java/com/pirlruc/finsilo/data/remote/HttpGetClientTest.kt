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
    fun clientFollowsAllowlistedRedirectsOnly() {
        val client = marketHttpClient()
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
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
        validateMarketGet(
            Request.Builder().url("https://stooq.pl/q/d/l/?s=xauusd&i=d").get().build(),
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

    @Test
    fun httpFailureMessageOmitsQuerySecrets() {
        val message = httpFailureMessage(401, "https://www.alphavantage.co/query?apikey=SECRET&function=OVERVIEW")
        assertFalse(message.contains("SECRET"))
        assertFalse(message.contains("apikey"))
        assertTrue(message.contains("www.alphavantage.co"))
        assertTrue(message.contains("401"))
    }

    @Test
    fun sanitizedIoRedactsQueryAndApiKey() {
        val url = "https://www.alphavantage.co/query?apikey=SECRET&function=OVERVIEW"
        val leaked = sanitizedIo(IOException("Failed GET $url"), url)
        assertFalse(leaked.message!!.contains("SECRET"))
        assertFalse(leaked.message!!.contains("apikey"))
        assertTrue(leaked.message!!.contains("www.alphavantage.co"))
    }

    @Test
    fun sanitizedIoKeepsStatusAndSizeMessages() {
        val url = "https://stooq.com/q/d/l"
        assertEquals("HTTP 401 for stooq.com", sanitizedIo(IOException(httpFailureMessage(401, url)), url).message)
        assertEquals("Quote response is too large", sanitizedIo(IOException("Quote response is too large"), url).message)
    }
}
