package com.pirlruc.finsilo.domain.backup

import java.net.URI

/** PROPFIND, PUT, and GET for one encrypted ledger file. The password stays out of the URL. */
object WebDavSync {
    const val FILE_NAME: String = "FSILO-LEDGER"

    fun allowed(method: String, url: String): Boolean {
        if (method !in setOf("PROPFIND", "PUT", "GET")) return false
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        return parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.userInfo == null && parsed.path.endsWith(FILE_NAME)
    }

    /** True when the server has a different copy and this device must not overwrite it. */
    fun conflict(storedEtag: String?, remoteEtag: String?): Boolean =
        !storedEtag.isNullOrBlank() && !remoteEtag.isNullOrBlank() && storedEtag != remoteEtag
}

data class WebDavExchange(val code: Int, val bytes: ByteArray, val etag: String?)

/** PROPFIND, then PUT of the encrypted backup. A different ETag is not overwritten. */
class WebDavClient(private val call: suspend (String, String, Map<String, String>, ByteArray) -> WebDavExchange) {
    suspend fun upload(
        url: String,
        user: String,
        password: String,
        bytes: ByteArray,
        storedEtag: String?,
        overwrite: Boolean = false,
    ): WebDavResult {
        if (!WebDavSync.allowed("PROPFIND", url) || !WebDavSync.allowed("PUT", url)) return WebDavResult(null, REFUSED)
        val remote = lookup(url, user, password)
        if (remote.error != null) return WebDavResult(null, remote.error)
        val stored = storedEtag?.takeIf { it.isNotBlank() }
        if (!overwrite && stored == null && !remote.etag.isNullOrBlank()) return WebDavResult(remote.etag, NEWER)
        if (!overwrite && WebDavSync.conflict(stored, remote.etag)) return WebDavResult(remote.etag, NEWER)
        return put(url, user, password, bytes, stored, remote.etag, overwrite)
    }

    suspend fun download(url: String, user: String, password: String): WebDavResult {
        if (!WebDavSync.allowed("GET", url)) return WebDavResult(null, REFUSED)
        val got = call("GET", url, auth(user, password), ByteArray(0))
        return if (got.code == HTTP_OK) WebDavResult(got.etag, null, got.bytes) else WebDavResult(null, "WebDAV download failed (HTTP ${got.code}).")
    }

    private suspend fun lookup(url: String, user: String, password: String): Found {
        val probe = call("PROPFIND", url, auth(user, password) + mapOf("Depth" to "0"), PROPFIND)
        if (probe.code == HTTP_NOT_FOUND) return Found(null, null)
        if (probe.code != HTTP_OK && probe.code != HTTP_MULTI) return Found(null, "WebDAV lookup failed (HTTP ${probe.code}).")
        return Found(probe.etag ?: etagIn(probe.bytes), null)
    }

    private suspend fun put(
        url: String,
        user: String,
        password: String,
        bytes: ByteArray,
        stored: String?,
        remote: String?,
        overwrite: Boolean,
    ): WebDavResult {
        val match = when {
            overwrite && !remote.isNullOrBlank() -> mapOf("If-Match" to remote)
            stored.isNullOrBlank() -> mapOf("If-None-Match" to "*")
            else -> mapOf("If-Match" to (remote ?: stored))
        }
        val put = call("PUT", url, auth(user, password) + match, bytes)
        if (put.code == HTTP_PRECONDITION) return WebDavResult(remote, NEWER)
        val accepted = put.code == HTTP_CREATED || put.code == HTTP_NO_CONTENT || put.code == HTTP_OK
        return if (accepted) WebDavResult(put.etag ?: remote, null) else WebDavResult(null, "WebDAV upload failed (HTTP ${put.code}).")
    }

    private fun auth(user: String, password: String): Map<String, String> = mapOf("Authorization" to basic(user, password))

    private fun basic(user: String, password: String): String {
        val raw = "$user:$password".toByteArray(Charsets.UTF_8)
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw)
    }

    private fun etagIn(bytes: ByteArray): String? = ETAG.find(bytes.toString(Charsets.UTF_8))?.groupValues?.get(1)

    private companion object {
        const val HTTP_OK: Int = 200
        const val HTTP_CREATED: Int = 201
        const val HTTP_NO_CONTENT: Int = 204
        const val HTTP_MULTI: Int = 207
        const val HTTP_NOT_FOUND: Int = 404
        const val HTTP_PRECONDITION: Int = 412
        const val REFUSED: String = "WebDAV URL is not an https FSILO-LEDGER path."
        const val NEWER: String = "WebDAV has a newer backup. Download it before uploading."
        val PROPFIND: ByteArray = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><getetag/></prop></propfind>""".toByteArray()
        val ETAG: Regex = Regex("<(?:\\w+:)?getetag>([^<]+)</(?:\\w+:)?getetag>", RegexOption.IGNORE_CASE)
    }
}

private data class Found(val etag: String?, val error: String?)

data class WebDavResult(val etag: String?, val error: String?, val bytes: ByteArray = ByteArray(0))
