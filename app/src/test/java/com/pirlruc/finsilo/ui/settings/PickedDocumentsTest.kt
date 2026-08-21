package com.pirlruc.finsilo.ui.settings

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickedDocumentsTest {
    @Test
    fun taxExportWritesWithoutBackupName() {
        val csv = "a,b".toByteArray()
        val plan = planPickedWrite(uriPresent = true, pending = csv, backup = null, backupNamed = false)
        assertTrue(plan.bytes.contentEquals(csv))
        assertFalse(plan.consumeBackup)
        assertFalse(plan.missingBackup)
    }

    @Test
    fun backupCancelClearsPendingNameWithoutWrite() {
        val blob = ByteArray(4) { 1 }
        val plan = planPickedWrite(uriPresent = false, pending = null, backup = blob, backupNamed = true)
        assertNull(plan.bytes)
        assertTrue(plan.consumeBackup)
        assertFalse(plan.missingBackup)
    }

    @Test
    fun missingBackupBytesAfterPickerReportsLoss() {
        val plan = planPickedWrite(uriPresent = true, pending = null, backup = null, backupNamed = true)
        assertNull(plan.bytes)
        assertTrue(plan.missingBackup)
        assertFalse(plan.consumeBackup)
    }

    @Test
    fun restoreReadRejectsOversizeInput() {
        val data = ByteArray(32) { 7 }
        val ok = BoundedBytes.read(ByteArrayInputStream(data), maxBytes = 64)
        assertTrue(ok.contentEquals(data))
        try {
            BoundedBytes.read(ByteArrayInputStream(data), maxBytes = 8)
            error("expected oversize failure")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("too large"))
        }
    }
}
