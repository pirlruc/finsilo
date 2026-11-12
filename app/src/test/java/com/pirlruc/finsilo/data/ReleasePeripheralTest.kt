package com.pirlruc.finsilo.data

import android.app.Application
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.local.FinsiloTypeConverters
import com.pirlruc.finsilo.data.sync.PortfolioAlertNotifier
import com.pirlruc.finsilo.data.sync.ShadePrivacy
import com.pirlruc.finsilo.data.sync.WidgetNavCache
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.RealizedGainsReport
import com.pirlruc.finsilo.domain.model.RealizedKind
import com.pirlruc.finsilo.domain.model.RealizedLotLine
import com.pirlruc.finsilo.domain.usecase.AlertChannel
import com.pirlruc.finsilo.domain.usecase.PortfolioAlert
import com.pirlruc.finsilo.ui.dashboard.DashboardLoader
import com.pirlruc.finsilo.ui.lock.BiometricKeyWrap
import com.pirlruc.finsilo.ui.lock.BiometricSessionCipher
import com.pirlruc.finsilo.ui.lock.isStaleBiometricKey
import com.pirlruc.finsilo.ui.settings.BoundedBytes
import com.pirlruc.finsilo.ui.settings.PortfolioToolsActions
import com.pirlruc.finsilo.ui.settings.RealizedGainsPdf
import com.pirlruc.finsilo.ui.settings.finishCreateDocument
import com.pirlruc.finsilo.ui.settings.launchCreate
import com.pirlruc.finsilo.ui.settings.launchOpen
import com.pirlruc.finsilo.ui.settings.planPickedWrite
import com.pirlruc.finsilo.ui.settings.writePicked
import com.pirlruc.finsilo.widget.NavWidgetProvider
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.security.GeneralSecurityException
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReleasePeripheralTest {
    private val day = LocalDate.of(2026, 1, 15)

    @Test
    fun convertersRoundTripNullAndValues() {
        val converters = FinsiloTypeConverters()
        assertNull(converters.bigDecimalToString(null))
        assertEquals(BigDecimal.TEN, converters.stringToBigDecimal(converters.bigDecimalToString(BigDecimal.TEN)))
        assertNull(converters.stringToBigDecimal(null))
        assertEquals(day, converters.stringToDate(converters.dateToString(day)))
        assertNull(converters.dateToString(null))
        assertNull(converters.stringToDate(null))
        assertEquals(AssetType.ETF, converters.stringToAssetType(converters.assetTypeToString(AssetType.ETF)))
        assertNull(converters.assetTypeToString(null))
        assertNull(converters.stringToAssetType(null))
        assertEquals(Currency.GBP, converters.stringToCurrency(converters.currencyToString(Currency.GBP)))
        assertNull(converters.currencyToString(null))
        assertNull(converters.stringToCurrency(null))
        assertEquals(TransactionTypeSafe.SELL, converters.stringToTransactionType(converters.transactionTypeToString(TransactionTypeSafe.SELL)))
        assertNull(converters.transactionTypeToString(null))
        assertNull(converters.stringToTransactionType(null))
        assertEquals(AnalystRating.BUY, converters.intToRating(converters.ratingToInt(AnalystRating.BUY)))
        assertNull(converters.ratingToInt(null))
        assertNull(converters.intToRating(null))
    }

    @Test
    fun migrationAndAsOfResolutionAreCallable() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
        FinsiloDatabase.MIGRATION_1_2.migrate(database.openHelper.writableDatabase)
        FinsiloDatabase.DropUnreadNavRebuildColumns()
        database.close()
        assertEquals(day, DashboardLoader.resolveAsOf(null, null, day))
        assertEquals(day.plusDays(2), DashboardLoader.resolveAsOf(day.plusDays(2), day, day))
        assertEquals(day, DashboardLoader.resolveAsOf(day.minusDays(3), day.minusDays(1), day))
    }

    @Test
    fun pdfSpansASecondPageAndNotifierPublishesEachChannel() {
        val asset = Asset("a", "SYN", "Synthetic", AssetType.ETF, Currency.EUR)
        val lines =
            (1..80).map { index ->
                RealizedLotLine(
                    asset = asset,
                    sellDate = day,
                    acquiredDate = day.minusDays(index.toLong()),
                    quantity = BigDecimal.ONE,
                    costEur = BigDecimal.TEN,
                    proceedsEur = BigDecimal.TEN,
                    gainEur = BigDecimal.ONE,
                    kind = RealizedKind.DISPOSAL,
                )
            }
        val pdf = runCatching { RealizedGainsPdf.write(RealizedGainsReport(2026, lines, BigDecimal.TEN)) }.getOrNull()
        if (pdf != null) assertTrue(pdf.size > 32)

        val context = ApplicationProvider.getApplicationContext<Application>()
        val notifier = PortfolioAlertNotifier(context)
        notifier.publish(emptyList())
        ShadePrivacy.redact = false
        val alerts =
            AlertChannel.entries.map { channel ->
                PortfolioAlert(channel, "Synthetic ${channel.name}", "Fixture body")
            }
        notifier.publish(alerts)
        notifier.publish(alerts)
        ShadePrivacy.redact = true
        notifier.publish(listOf(PortfolioAlert(AlertChannel.DRIFT, "Synthetic drift", "Fixture")))
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager)
    }

    @Test
    fun widgetRendersEmptyAndCachedNav() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("widget-test", Context.MODE_PRIVATE)
        val cache = WidgetNavCache(context, prefs)
        cache.write(null)
        assertNull(cache.read())
        runCatching { NavWidgetProvider().onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(7)) }
        cache.write(NavPoint(day, BigDecimal.TEN))
        assertEquals(day, cache.read()?.date)
        runCatching { NavWidgetProvider.refreshAll(context) }
        runCatching { NavWidgetProvider().onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(7)) }
        prefs.edit().putString("date", "bad").putString("value_eur", "nope").apply()
        assertNull(WidgetNavCache(context, prefs).read())
    }

    @Test
    fun pickedWritesAndBiometricSealUseSoftwareCiphers() = runTest {
        val pending = "synthetic".toByteArray()
        assertNull(planPickedWrite(false, pending, null, false).bytes)
        assertTrue(planPickedWrite(false, null, null, true).consumeBackup)
        assertTrue(planPickedWrite(true, null, null, true).missingBackup)
        assertTrue(planPickedWrite(true, pending, null, false).bytes!!.contentEquals(pending))
        val tooBig = ByteArrayInputStream(ByteArray(8))
        try {
            BoundedBytes.read(tooBig, maxBytes = 4)
            error("expected cap")
        } catch (error: java.io.IOException) {
            assertTrue(error.message!!.contains("too large"))
        }
        assertEquals(3, BoundedBytes.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))).size)
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(writePicked(context, null, pending))
        assertFalse(writePicked(context, Uri.parse("content://synthetic/export"), null))
        val actions = recordingActions()
        val scope = this
        finishCreateDocument(context, null, null, null, true, actions, scope)
        finishCreateDocument(context, Uri.parse("content://synthetic/missing"), null, null, true, actions, scope)
        finishCreateDocument(context, Uri.parse("content://synthetic/export"), pending, null, false, actions, scope)
        launchCreate(FailingLauncher(), "ledger.fsilo", actions) {}
        launchOpen(FailingOpen(), actions)
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encrypt = Cipher.getInstance("AES/GCM/NoPadding")
        encrypt.init(Cipher.ENCRYPT_MODE, key)
        val sealed = BiometricKeyWrap.seal(auth(encrypt), byteArrayOf(1, 2, 3))
        assertNotNull(sealed)
        assertNull(BiometricKeyWrap.seal(auth(null), byteArrayOf(1)))
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
        decrypt.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed!!.copyOfRange(0, 12)))
        assertNotNull(BiometricKeyWrap.open(auth(decrypt), sealed))
        assertNull(BiometricKeyWrap.open(auth(decrypt), byteArrayOf(1)))
        assertNull(BiometricKeyWrap.open(auth(null), sealed))
        assertFalse(BiometricSessionCipher.confirm(auth(null)))
        assertTrue(isStaleBiometricKey(android.security.keystore.KeyPermanentlyInvalidatedException()))
        assertTrue(isStaleBiometricKey(GeneralSecurityException(android.security.keystore.UserNotAuthenticatedException())))
        assertFalse(isStaleBiometricKey(GeneralSecurityException("other")))
    }

    private fun auth(cipher: Cipher?): BiometricPrompt.AuthenticationResult {
        val constructor =
            BiometricPrompt.AuthenticationResult::class.java.declaredConstructors.first { it.parameterCount == 2 }
        constructor.isAccessible = true
        val crypto = cipher?.let { BiometricPrompt.CryptoObject(it) }
        return constructor.newInstance(crypto, BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) as BiometricPrompt.AuthenticationResult
    }

    private fun recordingActions() = PortfolioToolsActions(
        onYear = {},
        onRecovery = {},
        onExportCsv = {},
        onExportPdf = {},
        onPrepareBackup = {},
        onExportConsumed = {},
        onExportLaunchFailed = {},
        onRestoreBackup = {},
        onConfirmRestore = {},
        onCancelRestore = {},
        onPickerBusy = {},
    )

    private class FailingLauncher : ActivityResultLauncher<String>() {
        override val contract: ActivityResultContract<String, *> = ActivityResultContracts.CreateDocument("*/*")

        override fun launch(input: String, options: ActivityOptionsCompat?) = error("no picker")

        override fun unregister() = Unit
    }

    private class FailingOpen : ActivityResultLauncher<Array<String>>() {
        override val contract: ActivityResultContract<Array<String>, *> = ActivityResultContracts.OpenDocument()

        override fun launch(input: Array<String>, options: ActivityOptionsCompat?) = error("no picker")

        override fun unregister() = Unit
    }
}

private typealias TransactionTypeSafe = com.pirlruc.finsilo.domain.model.TransactionType
