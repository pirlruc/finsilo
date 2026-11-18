package com.pirlruc.finsilo.data.remote

import com.pirlruc.finsilo.domain.backup.WebDavExchange
import com.pirlruc.finsilo.domain.importcsv.BrokerCall
import com.pirlruc.finsilo.domain.importcsv.BrokerExchange
import com.pirlruc.finsilo.domain.importcsv.ReadOnlyBrokers
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** HTTPS calls for the read-only brokers. A path outside the allow-list is not sent. */
class OkHttpBrokerCall : BrokerCall {
    override suspend fun call(method: String, url: String, headers: Map<String, String>, body: String): BrokerExchange {
        if (!allowed(method, url)) throw IOException("Broker request refused")
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (name, value) -> builder.header(name, value) }
            if (method == "POST") builder.post(body.toRequestBody(FORM)) else builder.get()
            trading212HttpClient().newCall(builder.build()).execute().use { response ->
                BrokerExchange(response.code, readCapped(response.body.byteStream()))
            }
        }
    }

    private fun allowed(method: String, url: String): Boolean =
        ReadOnlyBrokers.ibkrAllowed(method, url) ||
            ReadOnlyBrokers.krakenAllowed(method, url) ||
            ReadOnlyBrokers.coinbaseAllowed(method, url)

    private companion object {
        val FORM = "application/x-www-form-urlencoded".toMediaType()
    }
}

/** PROPFIND and PUT for one encrypted backup. The password is a header, not part of the URL. */
class OkHttpWebDavCall {
    suspend fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray): WebDavExchange {
        if (method != "PROPFIND" && method != "PUT" && method != "GET") throw IOException("WebDAV method refused")
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (name, value) -> builder.header(name, value) }
            when (method) {
                "GET" -> builder.get()
                else -> builder.method(method, body.toRequestBody(BYTES))
            }
            trading212HttpClient().newCall(builder.build()).execute().use { response ->
                val bytes = response.body.byteStream().readNBytes(TRADING212_MAX_BODY_BYTES + 1)
                if (bytes.size > TRADING212_MAX_BODY_BYTES) throw IOException("WebDAV response is too large")
                WebDavExchange(response.code, bytes, response.header("ETag"))
            }
        }
    }

    private companion object {
        val BYTES = "application/octet-stream".toMediaType()
    }
}

private fun readCapped(stream: java.io.InputStream): String {
    val bytes = stream.readNBytes(TRADING212_MAX_BODY_BYTES + 1)
    if (bytes.size > TRADING212_MAX_BODY_BYTES) throw IOException("Broker response is too large")
    return bytes.toString(StandardCharsets.UTF_8)
}
