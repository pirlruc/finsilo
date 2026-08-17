package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class GetOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") {
            throw IOException("FinSilo allows GET only; refused ${request.method} ${request.url}")
        }
        return chain.proceed(request)
    }
}

class HttpGetClient(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(GetOnlyInterceptor())
            .callTimeout(30, TimeUnit.SECONDS)
            .build(),
) {
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            body
        }
    }
}
