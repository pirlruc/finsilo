package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource

internal fun validateMarketGet(request: Request) {
    if (request.method != "GET") {
        throw IOException("FinSilo allows GET only; refused ${request.method}")
    }
    MarketHttpsPolicy.requireHttps(request.url)
}

class GetOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        validateMarketGet(request)
        return chain.proceed(request)
    }
}

internal fun marketHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .addNetworkInterceptor(GetOnlyInterceptor())
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

internal const val MARKET_RESPONSE_MAX_BYTES: Long = 8L * 1024 * 1024

internal fun quoteRequestFailed(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    return "Quote request failed for $host"
}

internal fun httpFailureMessage(code: Int, url: String): String {
    val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    return "HTTP $code for $host"
}

internal fun sanitizedIo(error: IOException, url: String): IOException {
    val message = error.message ?: return IOException(quoteRequestFailed(url))
    if (keepHttpMessage(message)) return error
    return IOException(quoteRequestFailed(url))
}

private val keptHttpPrefixes =
    listOf("HTTP ", "FinSilo allows", "Host not allowed", "HTTPS port", "Invalid", "URL ")

private fun keepHttpMessage(message: String): Boolean {
    if (message.contains("apikey", ignoreCase = true) || message.contains('?')) return false
    return message == "Quote response is too large" || keptHttpPrefixes.any { message.startsWith(it) }
}

internal fun readUtf8Capped(source: BufferedSource, maxBytes: Long): String {
    val buffer = Buffer()
    while (!source.exhausted()) {
        source.read(buffer, 8192)
        if (buffer.size > maxBytes) throw IOException("Quote response is too large")
    }
    return buffer.readUtf8()
}

class HttpGetClient(private val client: OkHttpClient = marketHttpClient()) {
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        MarketHttpsPolicy.requireHttpsUrl(url)
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(httpFailureMessage(response.code, url))
                }
                readUtf8Capped(response.body.source(), MARKET_RESPONSE_MAX_BYTES)
            }
        } catch (error: IOException) {
            throw sanitizedIo(error, url)
        }
    }
}
