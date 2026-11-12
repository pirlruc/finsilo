package com.pirlruc.finsilo.domain.usecase

import java.security.MessageDigest

/**
 * Dedup token for a portfolio alert.
 *
 * The stored value is a SHA-256 hex digest so preference files do not keep
 * holding names, ratings, or prices.
 */
object AlertPublishKey {
    /** Cap on stored digests so the preference file cannot grow without bound. */
    const val MAX_STORED: Int = 256

    /**
     * Keep at most [MAX_STORED] digests. [next] is retained. Older entries
     * drop from the front of [existing] iteration order.
     */
    fun bound(existing: Set<String>, next: String, max: Int = MAX_STORED): Set<String> {
        val ordered = existing.toMutableList()
        ordered.remove(next)
        ordered.add(next)
        val overflow = ordered.size - max
        if (overflow > 0) {
            repeat(overflow) { ordered.removeAt(0) }
        }
        return ordered.toSet()
    }

    /** Hex SHA-256 of channel, title, and body. */
    fun digest(channel: String, title: String, body: String): String {
        val raw = "$channel|$title|$body".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(raw)
        return hash.joinToString("") { byte -> "%02x".format(byte) }
    }
}
