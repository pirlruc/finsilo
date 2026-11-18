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
