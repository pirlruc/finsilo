package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val TRADING212_MAX_BODY_BYTES = 2 * 1024 * 1024

internal fun trading212HttpClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

/** HTTP Basic against live.trading212.com. The key and secret stay out of the URL and out of logs. */
class OkHttpTrading212Transport(private val client: OkHttpClient = trading212HttpClient()) : Trading212Transport {
    override suspend fun get(method: String, url: String, key: String, secret: String): Trading212HttpResult {
        if (!trading212RequestAllowed(method, url)) throw IOException("Trading 212 request refused for live.trading212.com")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(key, secret))
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val bytes = response.body.byteStream().readNBytes(TRADING212_MAX_BODY_BYTES + 1)
                if (bytes.size > TRADING212_MAX_BODY_BYTES) {
                    throw IOException("Trading 212 response exceeds $TRADING212_MAX_BODY_BYTES bytes")
                }
                Trading212HttpResult(response.code, bytes.toString(StandardCharsets.UTF_8))
            }
        }
    }
}
