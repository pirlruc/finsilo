package com.pirlruc.finsilo.data.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val TRADING212_MAX_BODY_BYTES = 2 * 1024 * 1024

internal fun readAtMost(stream: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total < limit) {
        val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}

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
                val bytes = readAtMost(response.body.byteStream(), TRADING212_MAX_BODY_BYTES + 1)
                if (bytes.size > TRADING212_MAX_BODY_BYTES) {
                    throw IOException("Trading 212 response exceeds $TRADING212_MAX_BODY_BYTES bytes")
                }
                Trading212HttpResult(response.code, bytes.toString(StandardCharsets.UTF_8))
            }
        }
    }
}
