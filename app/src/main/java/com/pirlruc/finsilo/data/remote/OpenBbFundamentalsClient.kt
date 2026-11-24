package com.pirlruc.finsilo.data.remote

import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.fundamentals.FundamentalView
import com.pirlruc.finsilo.domain.fundamentals.OpenBbFundamentals
import java.math.BigDecimal
import java.time.LocalDate

/** Optional OpenBB reader. It stays off until a vault stores an https base URL. */
class OpenBbFundamentalsClient(
    private val http: HttpGetClient,
    private val keys: DatabaseKeyStore,
) {
    fun enabled(): Boolean = OpenBbFundamentals.host(keys.openBbBaseUrl().orEmpty()) != null

    suspend fun load(symbol: String, price: BigDecimal?, asOf: LocalDate): FundamentalView {
        val base = keys.openBbBaseUrl() ?: return FundamentalView.missing()
        val host = OpenBbFundamentals.host(base) ?: return FundamentalView.missing()
        MarketHttpsPolicy.allowHost(host)
        val token = keys.openBbToken()
        val headers = if (token.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token")
        val metrics = OpenBbFundamentals.metricsUrl(base, symbol) ?: return FundamentalView.missing()
        val ratios = OpenBbFundamentals.ratiosUrl(base, symbol) ?: return FundamentalView.missing()
        return runCatching { OpenBbFundamentals.view(http.get(metrics, headers), http.get(ratios, headers), price, asOf) }
            .getOrDefault(FundamentalView.missing())
    }
}
