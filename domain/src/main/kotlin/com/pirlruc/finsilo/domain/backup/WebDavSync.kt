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
    suspend fun upload(url: String, user: String, password: String, bytes: ByteArray, storedEtag: String?): WebDavResult {
        if (!WebDavSync.allowed("PROPFIND", url) || !WebDavSync.allowed("PUT", url)) return WebDavResult(null, REFUSED)
        val auth = mapOf("Authorization" to basic(user, password))
        val probe = call("PROPFIND", url, auth + mapOf("Depth" to "0"), PROPFIND)
        if (probe.code != HTTP_OK && probe.code != HTTP_MULTI) return WebDavResult(null, "WebDAV lookup failed (HTTP ${probe.code}).")
        val remote = probe.etag ?: etagIn(probe.bytes)
        if (WebDavSync.conflict(storedEtag, remote)) return WebDavResult(null, "WebDAV has a newer backup. Download it before uploading.")
        val putHeaders = if (remote.isNullOrBlank()) auth else auth + mapOf("If-Match" to remote)
        val put = call("PUT", url, putHeaders, bytes)
        if (put.code != HTTP_CREATED && put.code != HTTP_NO_CONTENT && put.code != HTTP_OK) {
            return WebDavResult(null, "WebDAV upload failed (HTTP ${put.code}).")
        }
        return WebDavResult(put.etag ?: remote, null)
    }

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
        const val REFUSED: String = "WebDAV URL is not an https FSILO-LEDGER path."
        val PROPFIND: ByteArray = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><getetag/></prop></propfind>""".toByteArray()
        val ETAG: Regex = Regex("<(?:\\w+:)?getetag>([^<]+)</(?:\\w+:)?getetag>", RegexOption.IGNORE_CASE)
    }
}

data class WebDavResult(val etag: String?, val error: String?)
