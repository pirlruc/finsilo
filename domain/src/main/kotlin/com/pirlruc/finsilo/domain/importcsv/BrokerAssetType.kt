package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.AssetType

/** Infer [AssetType] from ticker/name/ISIN text in a broker CSV. */
internal object BrokerAssetType {
    private val cryptoTickers = setOf("BTC", "ETH", "SOL", "ADA", "XRP", "DOGE", "DOT", "AVAX", "LINK")

    fun infer(symbol: String, name: String, isin: String?): AssetType {
        val blob = "$symbol $name ${isin.orEmpty()}".uppercase()
        val ticker = symbol.substringBefore('.').substringBefore('_').uppercase()
        return when {
            ticker in cryptoTickers || "CRYPTO" in blob || "BITCOIN" in blob -> AssetType.CRYPTO
            ticker == "XAU" || blob.contains("GOLD") && "ETF" !in blob -> AssetType.COMMODITY
            looksEtf(blob, isin) -> AssetType.ETF
            else -> AssetType.STOCK
        }
    }

    private fun looksEtf(blob: String, isin: String?): Boolean {
        if (listOf("ETF", "UCITS", "VANGUARD", "ISHARES", "CORE MSCI").any { it in blob }) return true
        val code = isin?.uppercase().orEmpty()
        return code.startsWith("IE") || code.startsWith("LU")
    }
}
