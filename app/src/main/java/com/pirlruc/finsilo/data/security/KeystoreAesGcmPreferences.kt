package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/** [SharedPreferences] that stores values as Keystore AES-256-GCM blobs; key names stay plaintext. */
internal class KeystoreAesGcmPreferences(private val delegate: SharedPreferences, private val aead: PrefsAead) : SharedPreferences {
    private val listeners =
        ConcurrentHashMap<SharedPreferences.OnSharedPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> {
        val out = LinkedHashMap<String, Any?>()
        for (key in delegate.all.keys) {
            readValue(key)?.let { out[key] = it }
        }
        return out
    }

    override fun getString(key: String?, defValue: String?): String? = typed(key, defValue, PrefsPayload::asString)

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        typed(key, defValues, PrefsPayload::asStringSet)

    override fun getInt(key: String?, defValue: Int): Int = typed(key, defValue, PrefsPayload::asInt)

    override fun getLong(key: String?, defValue: Long): Long = typed(key, defValue, PrefsPayload::asLong)

    override fun getFloat(key: String?, defValue: Float): Float = typed(key, defValue, PrefsPayload::asFloat)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = typed(key, defValue, PrefsPayload::asBoolean)

    override fun contains(key: String?): Boolean {
        if (key.isNullOrEmpty()) return false
        return delegate.contains(key)
    }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener == null) return
        val bridge =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                listener.onSharedPreferenceChanged(this, changed)
            }
        listeners[listener] = bridge
        delegate.registerOnSharedPreferenceChangeListener(bridge)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener == null) return
        listeners.remove(listener)?.let { delegate.unregisterOnSharedPreferenceChangeListener(it) }
    }

    private fun <T> typed(key: String?, defValue: T, decode: (ByteArray) -> T?): T {
        val plain = plainOrNull(key) ?: return defValue
        return decode(plain) ?: throw ClassCastException(key)
    }

    private fun readValue(key: String): Any? {
        val plain = plainOrNull(key) ?: return null
        return PrefsPayload.decode(plain)
    }

    private fun plainOrNull(key: String?): ByteArray? {
        if (key.isNullOrEmpty()) return null
        val stored = delegate.getString(key, null) ?: return null
        val blob = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull() ?: return null
        return aead.open(key.toByteArray(StandardCharsets.UTF_8), blob)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val inner = delegate.edit()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (value == null) return remove(key)
            return putEncoded(key, PrefsPayload.encodeString(value))
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (values == null) return remove(key)
            return putEncoded(key, PrefsPayload.encodeStringSet(values))
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = putEncoded(key, PrefsPayload.encodeInt(value))

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = putEncoded(key, PrefsPayload.encodeLong(value))

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = putEncoded(key, PrefsPayload.encodeFloat(value))

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = putEncoded(key, PrefsPayload.encodeBoolean(value))

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key.isNullOrEmpty()) return this
            inner.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            inner.clear()
            return this
        }

        override fun commit(): Boolean = inner.commit()

        override fun apply() {
            inner.apply()
        }

        private fun putEncoded(key: String?, plain: ByteArray): SharedPreferences.Editor {
            if (key.isNullOrEmpty()) return this
            val sealed = aead.seal(key.toByteArray(StandardCharsets.UTF_8), plain)
            inner.putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP))
            return this
        }
    }
}
