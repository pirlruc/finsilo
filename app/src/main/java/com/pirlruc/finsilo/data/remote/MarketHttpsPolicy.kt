package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.net.URI
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** HTTPS host allowlist and path-token checks for quote-feed URLs. */
object MarketHttpsPolicy {
    val allowedHosts: Set<String> =
        setOf(
            "api.frankfurter.app",
            "www.alphavantage.co",
            "api.coingecko.com",
            "stooq.com",
        )

    private val safeToken = Regex("^[A-Za-z0-9._-]+$")

    fun requireHttpsUrl(url: String): URI = requireHttps(url.toHttpUrlOrNull() ?: reject("Invalid URL")).toUri()

    fun requireHttps(url: HttpUrl): HttpUrl {
        if (url.scheme != "https") {
            reject("FinSilo allows HTTPS only")
        }
        val host = url.host
        if (host !in allowedHosts) {
            reject("Host not allowed: $host")
        }
        if (url.port != 443) {
            reject("HTTPS port not allowed")
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            reject("URL userinfo is not allowed")
        }
        return url
    }

    fun requireSafeToken(value: String, label: String): String {
        if (value.contains("..") || !safeToken.matches(value)) {
            reject("Invalid $label")
        }
        return value
    }

    private fun reject(message: String): Nothing = throw IOException(message)
}
