package com.pirlruc.finsilo.data.remote

import java.io.IOException
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
        throw IOException("FinSilo allows GET only; refused ${request.method} ${request.url}")
    }
    MarketHttpsPolicy.requireHttpsUrl(request.url.toString())
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
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            readUtf8Capped(response.body.source(), MARKET_RESPONSE_MAX_BYTES)
        }
    }
}
