package com.pirlruc.finsilo.domain.importcsv

/** Localized header aliases for broker CSVs (accents already stripped by [normalizeHeader]). */
internal object BrokerHeaders {
    val TICKER = setOf("ticker", "symbol")
    val TYPE = setOf("type", "tipo")
    val PRODUCT = setOf("product", "produto", "produkt", "producto", "prodotto", "produit")
    val QUANTITY = setOf("aantal", "quantity", "quantidade", "cantidad", "quantite", "quantita", "stuck", "stuk")
    val PRICE = setOf("koers", "price", "preco", "precio", "prix", "kurs", "prezzo")
    val DESCRIPTION = setOf("omschrijving", "description", "descricao", "descripcion", "beschreibung", "descrizione")
    val CHANGE = setOf("mutatie", "change", "alteracao", "variacao", "variation", "anderung", "importe", "variazione")
    val BALANCE = setOf("saldo", "balance", "solde")
    val DATE = setOf("datum", "date", "data", "fecha")
    val VALUE = setOf("waarde", "value", "valor", "valeur", "wert", "valore")
    val TOTAL = setOf("totaal", "total", "totale", "gesamt")
    val LOCAL_CCY = setOf("lokale valuta", "local currency", "moeda local", "divisa local", "devise locale")
    val VALUE_CCY = setOf("valuta", "value currency", "moeda", "divisa", "devise", "wahrung")
    val FX = setOf(
        "wisselkoers",
        "exchange rate",
        "cambio",
        "tipo de cambio",
        "taux de change",
        "wechselkurs",
        "tasso di cambio",
        "fx",
    )
    val FEES = setOf(
        "transactiekosten",
        "transaction and/or third-party fees",
        "transaction costs",
        "custos de transacao",
        "gastos de transaccion",
        "frais de transaction",
        "transaktionsgebuhren",
        "commissioni",
    )

    fun any(names: Set<String>, aliases: Set<String>): Boolean =
        names.any { name -> name in aliases || aliases.any { alias -> name.startsWith(alias) } }
}
