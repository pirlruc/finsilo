package com.pirlruc.finsilo.data.sync

import com.pirlruc.finsilo.data.remote.OkHttpWebDavCall
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.backup.WebDavClient

/** Uploads the encrypted backup when a WebDAV URL, user, and app password are stored. */
class WebDavBackupUpload(private val keys: DatabaseKeyStore, private val client: WebDavClient = WebDavClient(OkHttpWebDavCall()::call)) {
    suspend fun upload(bytes: ByteArray): String? {
        val url = keys.webDavUrl() ?: return null
        val user = keys.webDavUser() ?: return "WebDAV URL is stored, but the user is missing."
        val password = keys.webDavPassword() ?: return "WebDAV URL is stored, but the app password is missing."
        val result = client.upload(url, user, password, bytes, keys.webDavEtag())
        if (result.error == null) keys.setWebDavEtag(result.etag)
        return result.error
    }
}
