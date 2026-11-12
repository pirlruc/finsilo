package com.pirlruc.finsilo.domain.importcsv

/** Map CSV headers onto a [BrokerCsvFormat]. */
internal object BrokerCsvDetect {
    fun from(headers: List<String>): BrokerCsvFormat? {
        val names = headers.map { normalizeHeader(it) }.toSet()
        return when {
            looksTrading212(names) -> BrokerCsvFormat.TRADING_212
            looksRevolutCommodity(names) -> BrokerCsvFormat.REVOLUT_STOCKS
            looksRevolutCrypto(names) -> BrokerCsvFormat.REVOLUT_STOCKS
            looksRevolut(names) -> BrokerCsvFormat.REVOLUT_STOCKS
            looksDegiroTransactions(names) -> BrokerCsvFormat.DEGIRO_TRANSACTIONS
            looksDegiroAccount(headers, names) -> BrokerCsvFormat.DEGIRO_ACCOUNT
            else -> null
        }
    }

    internal fun isCommodity(headers: List<String>): Boolean = looksRevolutCommodity(headers.map(::normalizeHeader).toSet())

    internal fun isCrypto(headers: List<String>): Boolean = looksRevolutCrypto(headers.map(::normalizeHeader).toSet())

    private fun looksTrading212(names: Set<String>): Boolean = "action" in names && ("no. of shares" in names || "ticker" in names)

    private fun looksRevolut(names: Set<String>): Boolean {
        val ticker = BrokerHeaders.any(names, BrokerHeaders.TICKER)
        val type = BrokerHeaders.any(names, BrokerHeaders.TYPE)
        val price = names.any { revolutPrice(it) }
        return ticker && type && price && "action" !in names
    }

    private fun revolutPrice(name: String): Boolean = name.startsWith("price per share") ||
        name.startsWith("precio por accion") ||
        name.startsWith("preco por acao") ||
        name == "price"

    private fun looksRevolutCommodity(names: Set<String>): Boolean {
        if ("action" in names || BrokerHeaders.any(names, BrokerHeaders.TICKER)) return false
        val type = BrokerHeaders.any(names, BrokerHeaders.TYPE)
        val product = BrokerHeaders.any(names, BrokerHeaders.PRODUCT)
        return type && product && commodityPocket(names)
    }

    private fun commodityPocket(names: Set<String>): Boolean {
        val amount = names.any { it == "amount" || it == "montant" }
        val currency = names.any { it == "currency" || it == "divisa" || it == "moeda" }
        val started = names.any { "started date" in it || it == "date started" }
        return amount && currency && started
    }

    private fun looksRevolutCrypto(names: Set<String>): Boolean {
        if ("action" in names) return false
        val price = "price" in names
        val fees = "fees" in names || "fee" in names
        return "symbol" in names &&
            BrokerHeaders.any(names, BrokerHeaders.TYPE) &&
            BrokerHeaders.any(names, BrokerHeaders.QUANTITY) &&
            price &&
            BrokerHeaders.any(names, BrokerHeaders.VALUE) &&
            fees &&
            BrokerHeaders.any(names, BrokerHeaders.DATE)
    }

    private fun looksDegiroAccount(headers: List<String>, names: Set<String>): Boolean {
        if (BrokerHeaders.any(names, BrokerHeaders.QUANTITY)) return false
        if (looksRevolutCommodity(names) || looksRevolutCrypto(names)) return false
        val description = BrokerHeaders.any(names, BrokerHeaders.DESCRIPTION)
        val balance = BrokerHeaders.any(names, BrokerHeaders.BALANCE)
        val change = BrokerHeaders.any(names, BrokerHeaders.CHANGE)
        if (description && (balance || change)) return true
        return splitCashLayout(headers) && (description || "isin" in names)
    }

    private fun looksDegiroTransactions(names: Set<String>): Boolean {
        if ("action" in names) return false
        val qty = BrokerHeaders.any(names, BrokerHeaders.QUANTITY)
        val product = BrokerHeaders.any(names, BrokerHeaders.PRODUCT)
        if (!qty || !product) return false
        val price = BrokerHeaders.any(names, BrokerHeaders.PRICE)
        val value = BrokerHeaders.any(names, BrokerHeaders.VALUE) || BrokerHeaders.any(names, BrokerHeaders.TOTAL)
        return price || value
    }

    private fun splitCashLayout(headers: List<String>): Boolean {
        if (headers.size < 12) return false
        return headers.getOrNull(8).isNullOrBlank() && headers.getOrNull(10).isNullOrBlank()
    }
}
