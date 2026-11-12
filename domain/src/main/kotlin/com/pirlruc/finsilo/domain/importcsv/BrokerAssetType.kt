package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.AssetType

/** Infer [AssetType] from ticker/name/ISIN text in a broker CSV. */
internal object BrokerAssetType {
    private val cryptoTickers = setOf(
        "ADA",
        "AVAX",
        "BONK",
        "BTC",
        "CRO",
        "DOGE",
        "DOT",
        "ENA",
        "ETH",
        "FLOKI",
        "LINK",
        "LMWR",
        "MEW",
        "PYTH",
        "ROSE",
        "SEI",
        "SOL",
        "VET",
        "XRP",
        "ZKJ",
    )
    private val commodityTickers = setOf("XAU", "XAG", "XPT", "XPD")

    fun infer(symbol: String, name: String, isin: String?): AssetType {
        val blob = "$symbol $name ${isin.orEmpty()}".uppercase()
        val ticker = symbol.substringBefore('.').substringBefore('_').uppercase()
        return when {
            ticker in cryptoTickers || "CRYPTO" in blob || "BITCOIN" in blob -> AssetType.CRYPTO
            ticker in commodityTickers || blob.contains("GOLD") && "ETF" !in blob -> AssetType.COMMODITY
            looksEtf(blob) -> AssetType.ETF
            else -> AssetType.STOCK
        }
    }

    private fun looksEtf(blob: String): Boolean = listOf("ETF", "UCITS", "VANGUARD", "ISHARES", "CORE MSCI").any { it in blob }
}
