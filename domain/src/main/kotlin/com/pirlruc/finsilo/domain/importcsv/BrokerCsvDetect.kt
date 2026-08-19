package com.pirlruc.finsilo.domain.importcsv

/** Map CSV headers onto a [BrokerCsvFormat]. */
internal object BrokerCsvDetect {
    fun from(headers: List<String>): BrokerCsvFormat? {
        val names = headers.map { normalizeHeader(it) }.toSet()
        return when {
            looksTrading212(names) -> BrokerCsvFormat.TRADING_212
            looksRevolut(names) -> BrokerCsvFormat.REVOLUT_STOCKS
            looksDegiroAccount(names) -> BrokerCsvFormat.DEGIRO_ACCOUNT
            looksDegiroTransactions(names) -> BrokerCsvFormat.DEGIRO_TRANSACTIONS
            else -> null
        }
    }

    private fun looksTrading212(names: Set<String>): Boolean = "action" in names && ("no. of shares" in names || "ticker" in names)

    private fun looksRevolut(names: Set<String>): Boolean {
        val ticker = "ticker" in names || "symbol" in names
        val price = names.any { it.startsWith("price per share") }
        return ticker && price && "type" in names && "action" !in names
    }

    private fun looksDegiroAccount(names: Set<String>): Boolean {
        val description = "omschrijving" in names || "description" in names
        val cash = "mutatie" in names || "change" in names || "saldo" in names || "balance" in names
        return description && cash
    }

    private fun looksDegiroTransactions(names: Set<String>): Boolean {
        val qty = "aantal" in names || "quantity" in names
        val price = "koers" in names || "price" in names
        val product = "product" in names
        return qty && price && product && "action" !in names
    }
}
