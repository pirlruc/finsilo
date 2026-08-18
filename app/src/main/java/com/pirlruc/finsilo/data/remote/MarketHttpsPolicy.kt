package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.net.URI

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

    fun requireHttpsUrl(url: String): URI {
        val uri = runCatching { URI(url) }.getOrElse { reject("Invalid URL") }
        if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) {
            reject("FinSilo allows HTTPS only")
        }
        val host = uri.host ?: reject("URL missing host")
        if (host !in allowedHosts) {
            reject("Host not allowed: $host")
        }
        if (uri.userInfo != null) {
            reject("URL userinfo is not allowed")
        }
        return uri
    }

    fun requireSafeToken(value: String, label: String): String {
        if (value.contains("..") || !safeToken.matches(value)) {
            reject("Invalid $label")
        }
        return value
    }

    private fun reject(message: String): Nothing = throw IOException(message)
}
