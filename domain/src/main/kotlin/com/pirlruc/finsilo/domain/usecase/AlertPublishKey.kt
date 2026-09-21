package com.pirlruc.finsilo.domain.usecase

import java.security.MessageDigest

/**
 * Dedup token for a portfolio alert.
 *
 * The stored value is a SHA-256 hex digest so preference files do not keep
 * holding names, ratings, or prices.
 */
object AlertPublishKey {
    /** Hex SHA-256 of channel, title, and body. */
    fun digest(channel: String, title: String, body: String): String {
        val raw = "$channel|$title|$body".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(raw)
        return hash.joinToString("") { byte -> "%02x".format(byte) }
    }
}
