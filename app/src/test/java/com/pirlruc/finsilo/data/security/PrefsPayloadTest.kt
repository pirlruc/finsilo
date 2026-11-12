package com.pirlruc.finsilo.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsPayloadTest {
    @Test
    fun decodeReadsEachTypedPayload() {
        assertEquals("eur", PrefsPayload.decode(PrefsPayload.encodeString("eur")))
        assertEquals(true, PrefsPayload.decode(PrefsPayload.encodeBoolean(true)))
        assertEquals(false, PrefsPayload.decode(PrefsPayload.encodeBoolean(false)))
        assertEquals(7, PrefsPayload.decode(PrefsPayload.encodeInt(7)))
        assertEquals(99L, PrefsPayload.decode(PrefsPayload.encodeLong(99L)))
        assertEquals(1.5f, PrefsPayload.decode(PrefsPayload.encodeFloat(1.5f)))
        assertEquals(setOf("a", "b"), PrefsPayload.decode(PrefsPayload.encodeStringSet(setOf("a", "b"))))
    }

    @Test
    fun decodeRejectsEmptyUnknownAndTruncatedPayloads() {
        assertNull(PrefsPayload.decode(byteArrayOf()))
        assertNull(PrefsPayload.decode(byteArrayOf(0)))
        assertNull(PrefsPayload.decode(byteArrayOf(9, 1)))
        assertNull(PrefsPayload.asString(byteArrayOf()))
        assertNull(PrefsPayload.asString(byteArrayOf(2, 1)))
        assertNull(PrefsPayload.asBoolean(byteArrayOf(2)))
        assertNull(PrefsPayload.asBoolean(byteArrayOf(1, 1)))
        assertNull(PrefsPayload.asInt(byteArrayOf(3, 1)))
        assertNull(PrefsPayload.asLong(PrefsPayload.encodeInt(1)))
        assertNull(PrefsPayload.asFloat(byteArrayOf(5)))
        assertNull(PrefsPayload.asStringSet(byteArrayOf()))
        assertNull(PrefsPayload.asStringSet(byteArrayOf(1)))
        assertNull(PrefsPayload.asStringSet(byteArrayOf(6, 0, 0, 0, 1)))
    }

    @Test
    fun booleanPayloadTreatsAnyNonZeroByteAsTrue() {
        assertEquals(true, PrefsPayload.asBoolean(byteArrayOf(2, 2)))
        assertEquals(false, PrefsPayload.asBoolean(byteArrayOf(2, 0)))
    }

    @Test
    fun stringSetKeepsDuplicatesAsOneEntry() {
        val decoded = PrefsPayload.decode(PrefsPayload.encodeStringSet(setOf("same", "same")))
        assertTrue(decoded is Set<*>)
        assertEquals(setOf("same"), decoded)
    }
}
