package com.pirlruc.finsilo.data.security

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** Typed plaintext packed inside a sealed SharedPreferences value. */
internal object PrefsPayload {
    private const val TYPE_STRING: Byte = 1
    private const val TYPE_BOOLEAN: Byte = 2
    private const val TYPE_INT: Byte = 3
    private const val TYPE_LONG: Byte = 4
    private const val TYPE_FLOAT: Byte = 5
    private const val TYPE_STRING_SET: Byte = 6
    private val utf8 = StandardCharsets.UTF_8
    private val order = ByteOrder.BIG_ENDIAN

    fun encodeString(value: String): ByteArray = byteArrayOf(TYPE_STRING) + value.toByteArray(utf8)

    fun encodeBoolean(value: Boolean): ByteArray = byteArrayOf(TYPE_BOOLEAN, if (value) 1 else 0)

    fun encodeInt(value: Int): ByteArray = typedNumber(TYPE_INT, 5).putInt(value).array()

    fun encodeLong(value: Long): ByteArray = typedNumber(TYPE_LONG, 9).putLong(value).array()

    fun encodeFloat(value: Float): ByteArray = typedNumber(TYPE_FLOAT, 5).putFloat(value).array()

    fun encodeStringSet(values: Set<String>): ByteArray {
        val encoded = values.map { it.toByteArray(utf8) }
        val size = 1 + 4 + encoded.sumOf { 4 + it.size }
        val buf = ByteBuffer.allocate(size).order(order).put(TYPE_STRING_SET).putInt(encoded.size)
        for (item in encoded) {
            buf.putInt(item.size)
            buf.put(item)
        }
        return buf.array()
    }

    fun asString(plain: ByteArray): String? {
        if (plain.isEmpty() || plain[0] != TYPE_STRING) return null
        return String(plain, 1, plain.size - 1, utf8)
    }

    fun asBoolean(plain: ByteArray): Boolean? {
        if (plain.size != 2 || plain[0] != TYPE_BOOLEAN) return null
        return plain[1] != 0.toByte()
    }

    fun asInt(plain: ByteArray): Int? = number(plain, TYPE_INT, 5)?.int

    fun asLong(plain: ByteArray): Long? = number(plain, TYPE_LONG, 9)?.long

    fun asFloat(plain: ByteArray): Float? = number(plain, TYPE_FLOAT, 5)?.float

    fun asStringSet(plain: ByteArray): MutableSet<String>? {
        if (plain.isEmpty() || plain[0] != TYPE_STRING_SET) return null
        return runCatching { readStringSet(plain) }.getOrNull()
    }

    fun decode(plain: ByteArray): Any? {
        if (plain.isEmpty()) return null
        return when (plain[0]) {
            TYPE_STRING -> asString(plain)
            TYPE_BOOLEAN -> asBoolean(plain)
            TYPE_INT -> asInt(plain)
            TYPE_LONG -> asLong(plain)
            TYPE_FLOAT -> asFloat(plain)
            TYPE_STRING_SET -> asStringSet(plain)
            else -> null
        }
    }

    private fun typedNumber(type: Byte, size: Int): ByteBuffer = ByteBuffer.allocate(size).order(order).put(type)

    private fun number(plain: ByteArray, type: Byte, size: Int): ByteBuffer? {
        if (plain.size != size || plain[0] != type) return null
        return ByteBuffer.wrap(plain, 1, size - 1).order(order)
    }

    private fun readStringSet(plain: ByteArray): MutableSet<String> {
        val buf = ByteBuffer.wrap(plain).order(order)
        buf.get()
        val count = buf.int
        val out = LinkedHashSet<String>(count)
        repeat(count) {
            val len = buf.int
            val bytes = ByteArray(len)
            buf.get(bytes)
            out.add(String(bytes, utf8))
        }
        return out
    }
}
