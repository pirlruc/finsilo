package com.pirlruc.finsilo.domain.importcsv

/** Map CSV headers onto a [BrokerCsvFormat]. */
internal object BrokerCsvDetect {
    fun from(headers: List<String>): BrokerCsvFormat? {
        val names = headers.map { normalizeHeader(it) }.toSet()
        return when {
            looksTrading212(names) -> BrokerCsvFormat.TRADING_212
            looksRevolut(names) -> BrokerCsvFormat.REVOLUT_STOCKS
            looksDegiroAccount(headers, names) -> BrokerCsvFormat.DEGIRO_ACCOUNT
            looksDegiroTransactions(names) -> BrokerCsvFormat.DEGIRO_TRANSACTIONS
            else -> null
        }
    }

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

    private fun looksDegiroAccount(headers: List<String>, names: Set<String>): Boolean {
        val description = BrokerHeaders.any(names, BrokerHeaders.DESCRIPTION)
        val cash = BrokerHeaders.any(names, BrokerHeaders.CHANGE) || BrokerHeaders.any(names, BrokerHeaders.BALANCE)
        if (description && cash) return true
        return splitCashLayout(headers) && (description || "isin" in names)
    }

    private fun looksDegiroTransactions(names: Set<String>): Boolean {
        val qty = BrokerHeaders.any(names, BrokerHeaders.QUANTITY)
        val price = BrokerHeaders.any(names, BrokerHeaders.PRICE)
        val product = BrokerHeaders.any(names, BrokerHeaders.PRODUCT)
        return qty && price && product && "action" !in names
    }

    private fun splitCashLayout(headers: List<String>): Boolean {
        if (headers.size < 12) return false
        return headers.getOrNull(8).isNullOrBlank() && headers.getOrNull(10).isNullOrBlank()
    }
}
