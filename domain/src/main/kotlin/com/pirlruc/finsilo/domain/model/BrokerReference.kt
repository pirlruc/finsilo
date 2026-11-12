package com.pirlruc.finsilo.domain.model

/** Stable Trading 212 fill id stored on the ledger row so a later sync does not double-book. */
object BrokerReference {
    const val PREFIX: String = "t212-"

    fun canonical(raw: String?): String? {
        val safe = raw?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }?.take(MAX)
        return safe?.takeIf { it.isNotBlank() }
    }

    fun ledgerId(raw: String?): String? = canonical(raw)?.let { PREFIX + it }

    fun fromLedgerId(id: String): String? = id.removePrefix(PREFIX).takeIf { id.startsWith(PREFIX) && it.isNotBlank() }

    private const val MAX: Int = 80
}
