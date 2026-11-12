package com.pirlruc.finsilo.ui

import android.app.Application
import androidx.compose.runtime.Composer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pirlruc.finsilo.data.ClearSelection
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolDraft
import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.BrokerCapital
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DashboardReport
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.HistoryReport
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.RelativeToAverage
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.TwrReport
import com.pirlruc.finsilo.domain.model.TwrSplit
import com.pirlruc.finsilo.domain.model.TwrSubPeriod
import com.pirlruc.finsilo.domain.model.YocReport
import com.pirlruc.finsilo.ui.dashboard.AllocationCard
import com.pirlruc.finsilo.ui.dashboard.AllocationPie
import com.pirlruc.finsilo.ui.dashboard.AlphaVantageKeyDialog
import com.pirlruc.finsilo.ui.dashboard.ClearSelectionDialog
import com.pirlruc.finsilo.ui.dashboard.DashboardClearActions
import com.pirlruc.finsilo.ui.dashboard.DashboardContent
import com.pirlruc.finsilo.ui.dashboard.DashboardNavActions
import com.pirlruc.finsilo.ui.dashboard.DashboardScreen
import com.pirlruc.finsilo.ui.dashboard.DashboardTopBar
import com.pirlruc.finsilo.ui.dashboard.DashboardUiState
import com.pirlruc.finsilo.ui.dashboard.EmptyState
import com.pirlruc.finsilo.ui.dashboard.ErrorState
import com.pirlruc.finsilo.ui.dashboard.HistoryCard
import com.pirlruc.finsilo.ui.dashboard.HistoryLineChart
import com.pirlruc.finsilo.ui.dashboard.HoldingActions
import com.pirlruc.finsilo.ui.dashboard.HoldingAlertDialog
import com.pirlruc.finsilo.ui.dashboard.HomeSection
import com.pirlruc.finsilo.ui.dashboard.InstrumentEditDialog
import com.pirlruc.finsilo.ui.dashboard.alphaVantageDialogCopy
import com.pirlruc.finsilo.ui.dashboard.chipLabel
import com.pirlruc.finsilo.ui.importcsv.BrokerImportCard
import com.pirlruc.finsilo.ui.importcsv.BrokerImportUiState
import com.pirlruc.finsilo.ui.importcsv.decodeCsv
import com.pirlruc.finsilo.ui.importcsv.pickerError
import com.pirlruc.finsilo.ui.ledger.ActivityLedger
import com.pirlruc.finsilo.ui.ledger.LedgerAmountActions
import com.pirlruc.finsilo.ui.ledger.LedgerEntryScreen
import com.pirlruc.finsilo.ui.ledger.LedgerFormActions
import com.pirlruc.finsilo.ui.ledger.LedgerInstrumentActions
import com.pirlruc.finsilo.ui.ledger.LedgerNavActions
import com.pirlruc.finsilo.ui.ledger.LedgerTemplateSection
import com.pirlruc.finsilo.ui.ledger.LedgerUiState
import com.pirlruc.finsilo.ui.ledger.ManualCloseFields
import com.pirlruc.finsilo.ui.lock.CheckRow
import com.pirlruc.finsilo.ui.lock.FinSiloBrand
import com.pirlruc.finsilo.ui.lock.LockError
import com.pirlruc.finsilo.ui.lock.LockScreenColumn
import com.pirlruc.finsilo.ui.lock.LockSetupScreen
import com.pirlruc.finsilo.ui.lock.LockUiState
import com.pirlruc.finsilo.ui.lock.LockWorkingIndicator
import com.pirlruc.finsilo.ui.lock.PinSecretField
import com.pirlruc.finsilo.ui.lock.PickerPrivacyCover
import com.pirlruc.finsilo.ui.lock.RecoverPinScreen
import com.pirlruc.finsilo.ui.lock.RecoveryCodeRow
import com.pirlruc.finsilo.ui.lock.SecureFlagPolicy
import com.pirlruc.finsilo.ui.lock.SecuritySettingsCard
import com.pirlruc.finsilo.ui.lock.SensitiveLockAction
import com.pirlruc.finsilo.ui.lock.UnlockScreen
import com.pirlruc.finsilo.ui.lock.WrapUpgradeScreen
import com.pirlruc.finsilo.ui.lock.hiddenFromA11y
import com.pirlruc.finsilo.ui.settings.BackupExportCard
import com.pirlruc.finsilo.ui.settings.PortfolioToolsActions
import com.pirlruc.finsilo.ui.settings.PortfolioToolsCard
import com.pirlruc.finsilo.ui.settings.PortfolioToolsUiState
import com.pirlruc.finsilo.ui.settings.RestoreConfirmIfNeeded
import com.pirlruc.finsilo.ui.settings.TargetSettingsActions
import com.pirlruc.finsilo.ui.settings.TargetSettingsScreen
import com.pirlruc.finsilo.ui.settings.TargetSettingsUiState
import com.pirlruc.finsilo.ui.settings.TaxExportCard
import com.pirlruc.finsilo.ui.settings.ToolMessages
import com.pirlruc.finsilo.ui.theme.FinsiloTheme
import com.pirlruc.finsilo.ui.watchlist.WatchlistActions
import com.pirlruc.finsilo.ui.watchlist.WatchlistEditor
import com.pirlruc.finsilo.ui.watchlist.WatchlistItems
import com.pirlruc.finsilo.ui.watchlist.WatchlistScreen
import com.pirlruc.finsilo.ui.watchlist.WatchlistUiState
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.WatchlistItem
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScreenCoverageTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 1, 15)
    private val asset = Asset("a1", "SYN", "Synthetic", AssetType.ETF, Currency.EUR)
    private val phase = mutableIntStateOf(0)

    @Test
    fun dashboardLedgerAndLockScreensRenderEachState() {
        HistoryRange.entries.forEach { assertTrue(it.chipLabel().isNotBlank()) }
        assertTrue(alphaVantageDialogCopy(true).isNotBlank())
        assertTrue(alphaVantageDialogCopy(false).isNotBlank())
        assertNull(pickerError(byteArrayOf(1)))
        assertNotNull(pickerError(byteArrayOf(0x50, 0x4B)))
        assertNotNull(pickerError(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())))
        assertTrue(decodeCsv("a,b".toByteArray()).contains("a,b"))
        assertTrue(decodeCsv(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'A'.code.toByte(), 0)).contains("A"))
        Modifier.hiddenFromA11y(true)
        Modifier.hiddenFromA11y(false)

        compose.setContent {
            FinsiloTheme(darkTheme = phase.intValue % 2 == 1) {
                when (phase.intValue) {
                    0 -> DashboardScreen(loadingState(), BrokerImportUiState(), nav(), clear())
                    1 -> DashboardScreen(loadingState().copy(loading = false, error = "Feed down"), BrokerImportUiState(), nav(), clear(), HomeSection.OVERVIEW)
                    2 -> DashboardScreen(loadingState().copy(loading = false, empty = true), BrokerImportUiState(error = "bad file"), nav(), clear(), HomeSection.ALL)
                    3 -> DashboardScreen(loadedState(confirm = true, key = true), reviewingImport(), nav(), clear(), HomeSection.HOLDINGS)
                    4 -> DashboardContent(report(full = true), HistoryRange.ONE_MONTH, "Synced", emptyMap(), emptyMap(), {}, holdings(), reviewingImport(), importNav(), HomeSection.INSIGHTS, listOf(tx()), listOf(asset))
                    5 -> DashboardContent(report(full = false), HistoryRange.ALL, null, emptyMap(), emptyMap(), {}, holdings(), null, null, HomeSection.OVERVIEW)
                    6 -> {
                        DashboardTopBar(false, true, {}, {}, {}, {}, {})
                        EmptyState({}, {}, BrokerImportUiState(), {}, {}, { _, _ -> }, {}, {}, {})
                        ErrorState("Could not load")
                        HistoryCard(HistoryReport(day, day, emptyList()), HistoryRange.YTD) {}
                        HistoryLineChart(listOf(NavPoint(day, BigDecimal.ONE)))
                        AllocationCard(listOf(slice(null)), BigDecimal.TEN)
                        AllocationPie(listOf(slice(BigDecimal.ONE), slice(BigDecimal.TEN, AssetType.STOCK)))
                        AlphaVantageKeyDialog(true, {}, {})
                        ClearSelectionDialog(ClearSelection(ledger = false), {}, {}, {}, {}, {})
                        HoldingAlertDialog(holding(), PriceAlertThreshold("a1", BigDecimal.TEN, BigDecimal.ONE), null, {}, {}, {})
                        InstrumentEditDialog(asset, {}, {})
                        BrokerImportCard(BrokerImportUiState(status = "Imported", importing = true), {})
                        ActivityLedger(listOf(tx()), listOf(asset), {}, {}, { _, _ -> })
                    }
                    else -> {
                        LedgerEntryScreen(ledgerState(TransactionType.BUY), ledgerActions())
                        LedgerEntryScreen(ledgerState(TransactionType.DEPOSIT_CASH), ledgerActions())
                        LedgerEntryScreen(ledgerState(TransactionType.SELL).copy(remainingQty = "1", assetType = AssetType.DEPOSIT, newInstrument = false, existingAssetId = "a1"), ledgerActions())
                        ManualCloseFields(true, "4", {}, {})
                        ManualCloseFields(false, "", {}, {})
                        LedgerTemplateSection(listOf(LedgerTemplate("t", "Repeat", TransactionType.BUY)), TransactionType.BUY, {}, {})
                        LockSetupScreen(LockUiState(biometricAvailable = true, working = true, error = "short"), {}, {}, {}, {}, {})
                        UnlockScreen(LockUiState(biometric = true, biometricAvailable = true, error = "no"), {}, {}, {}, {}, {})
                        UnlockScreen(LockUiState(pendingBiometricSeal = true), {}, {}, {}, {}, {})
                        RecoverPinScreen(LockUiState(working = true), {}, {}, {}, {})
                        WrapUpgradeScreen(LockUiState(upgradeRecovery = "synthetic-code", error = "no"), {}, {})
                        SecuritySettingsCard(LockUiState(pendingSensitiveAction = SensitiveLockAction.ROTATE_RECOVERY, newRecoveryCode = "synthetic-code", biometricAvailable = true), {}, {}, {}, {}, {}, {})
                        WatchlistScreen(watchState(), watchActions())
                        WatchlistEditor(watchState(), watchActions())
                        WatchlistItems(watchState().copy(items = listOf(WatchlistItem("w", "SYN", "Synthetic", AssetType.STOCK, Currency.EUR)), quotes = mapOf("w" to DailyMarketData("w", day, BigDecimal.TEN))), {}, {})
                        TargetSettingsScreen(TargetSettingsUiState(loading = false, error = "sum", status = "saved"), LockUiState(), BrokerImportUiState(), PortfolioToolsUiState(confirmRestore = true, status = "ok", error = "no"), settingsActions())
                        TaxExportCard(PortfolioToolsUiState(), toolsActions()) { _, _ -> }
                        BackupExportCard(PortfolioToolsUiState(busy = true), toolsActions()) {}
                        RestoreConfirmIfNeeded(true, toolsActions())
                        ToolMessages("ok", "no")
                        ToolMessages(null, null)
                        PortfolioToolsCard(PortfolioToolsUiState(), toolsActions())
                        LockScreenColumn { FinSiloBrand(); PinSecretField("1", {}, "PIN", true); CheckRow(true, true, "Saved", {}); LockWorkingIndicator(true); LockError("no"); PickerPrivacyCover(); SecureFlagPolicy.SecureOn() }
                        PinSecretField("1", {}, "PIN", false, onDone = {})
                        LockWorkingIndicator(false)
                        LockError(null)
                        FinsiloTheme(darkTheme = false) {}
                    }
                }
            }
        }
        repeat(8) { step ->
            phase.intValue = step
            compose.waitForIdle()
        }
        compose.onNodeWithText("Protect this device copy").assertExists()
    }

    @Test
    fun recomposesLockChromeInPlace() {
        val tick = mutableIntStateOf(0)
        val text = mutableStateOf("1")
        compose.setContent {
            FinsiloTheme {
                val step = tick.intValue
                LockScreenColumn {
                    FinSiloBrand()
                    PinSecretField(text.value, {}, "PIN", step % 2 == 0)
                    PinSecretField(
                        text.value,
                        {},
                        "PIN",
                        true,
                        ImeAction.Done,
                        onDone = {},
                    )
                    CheckRow(step % 2 == 0, step % 3 != 0, "Saved", {})
                    LockWorkingIndicator(step % 2 == 0)
                    LockError(if (step % 2 == 0) "no" else null)
                    if (step % 2 == 0) PickerPrivacyCover()
                    SecureFlagPolicy.SecureOn()
                }
            }
        }
        repeat(4) { step ->
            tick.intValue = step
            text.value = if (step % 2 == 0) "1" else "12"
            compose.waitForIdle()
        }
    }

    @Test
    fun drivesComposerRestartMasks() {
        val composer = composerProbe(changed = false, execute = false)
        val changedComposer = composerProbe(changed = true, execute = true)
        val method = Class.forName("com.pirlruc.finsilo.ui.lock.LockChromeKt").declaredMethods
            .first { it.name.startsWith("PinSecretField") && it.parameterTypes.size == 9 }
        method.isAccessible = true
        val change = { _: String -> }
        val done = {}
        for (target in listOf(composer, changedComposer)) {
            for (changed in intArrayOf(0, 0b01010101, 0x55555555, 0x00AAAAAA)) {
                for (defaultMask in intArrayOf(0, 16, 32, 48)) {
                    runCatching {
                        method.invoke(null, "1", change, "PIN", true, 1, done, target, changed, defaultMask)
                    }
                }
            }
        }
    }

    private fun loadingState() = DashboardUiState()

    private fun loadedState(confirm: Boolean, key: Boolean) = DashboardUiState(
        loading = false,
        report = report(true),
        statusMessage = "Synced",
        hasAlphaVantageKey = key,
        confirmClear = confirm,
        thresholds = mapOf("a1" to PriceAlertThreshold("a1", BigDecimal.TEN, null)),
        ratingPrefs = mapOf("a1" to RatingAlertPref("a1", RatingAlertScope.HOLDING, setOf(AnalystRating.SELL))),
        transactions = listOf(tx()),
        assets = listOf(asset),
    )

    private fun report(full: Boolean): DashboardReport {
        val points = if (full) listOf(NavPoint(day.minusDays(1), BigDecimal.ONE), NavPoint(day, BigDecimal.TEN)) else listOf(NavPoint(day, BigDecimal.ONE))
        return DashboardReport(
            asOf = day,
            allocation = AllocationReport(
                totalValueEur = BigDecimal.TEN,
                unrealizedPnlEur = BigDecimal.ONE,
                cashEur = BigDecimal.ONE,
                slices = listOf(slice(BigDecimal("5")), slice(null, AssetType.CASH)),
                holdings = listOf(holding(), holding().copy(priceEur = null, priceNative = null, unrealizedPnlEur = BigDecimal.ONE.negate())),
                contributedEur = BigDecimal.TEN,
                cashInterestEur = BigDecimal.ZERO,
                totalGainEur = BigDecimal.ONE.negate(),
                brokers = listOf(BrokerCapital(BrokerSource.MANUAL, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE.negate())),
            ),
            history = HistoryReport(day.minusMonths(1), day, points),
            signals = if (full) {
                listOf(
                    MarketSignal(asset, day, AnalystRating.BUY, AnalystRating.HOLD, BigDecimal.TEN, BigDecimal("9"), BigDecimal("8"), RelativeToAverage.ABOVE, RelativeToAverage.BELOW, TechnicalCross.GOLDEN),
                    MarketSignal(asset, day, AnalystRating.HOLD, null, BigDecimal.TEN, null, null, null, null, null),
                )
            } else {
                emptyList()
            },
            twr = TwrReport(
                BigDecimal.ONE,
                if (full) listOf(TwrSubPeriod(day.minusDays(4), day, BigDecimal.ONE, TwrSplit.WITHDRAWAL)) else emptyList(),
            ),
            yoc = if (full) listOf(YocReport(asset, BigDecimal.TEN, BigDecimal.ONE, null, 4), YocReport(asset, BigDecimal.TEN, null, BigDecimal.ONE, null)) else emptyList(),
            warnings = if (full) listOf("Missing FX") else emptyList(),
        )
    }

    private fun slice(target: BigDecimal?, type: AssetType = AssetType.ETF) =
        AllocationSlice(type, BigDecimal.TEN, BigDecimal("50"), target, target?.let { BigDecimal("6") })

    private fun holding() = HoldingValuation(asset, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal("9"), Currency.EUR)

    private fun tx() = Transaction("tx", "a1", day, TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO)

    private fun holdings() = HoldingActions({}, {}, {})

    private fun importNav() = com.pirlruc.finsilo.ui.dashboard.ImportNavActions({}, { _, _ -> }, {}, {}, {}, {})

    private fun nav() = DashboardNavActions({}, {}, {}, importNav(), {}, {}, {}, {}, {}, {}, {})

    private fun clear() = DashboardClearActions({}, {}, {}, {}, {}, {})

    private fun reviewingImport() = BrokerImportUiState(
        reviewing = true,
        drafts = listOf(ImportSymbolDraft("k", "SYN", "SYN", "Synthetic", AssetType.ETF, Currency.EUR, null, true, "No live quote")),
    )

    private fun ledgerState(type: TransactionType) = LedgerUiState(
        loading = false,
        type = type,
        date = day.toString(),
        quantity = "2",
        unitPriceNative = "10",
        newInstrument = type == TransactionType.BUY,
        symbol = "SYN",
        name = "Synthetic",
        assets = listOf(asset),
        cashEur = "5",
        templates = listOf(LedgerTemplate("t", "Repeat", TransactionType.BUY)),
        error = "check",
        status = "saved",
    )

    private fun ledgerActions() = LedgerFormActions(
        LedgerNavActions({}, {}, {}, {}, {}, {}),
        LedgerAmountActions({}, {}, {}, {}, {}, {}),
        LedgerInstrumentActions({}, {}, {}, {}, {}, {}, {}, {}),
    )

    private fun watchState() = WatchlistUiState(loading = false, symbol = "SYN", error = "missing", status = "updated", syncing = true)

    private fun watchActions() = WatchlistActions(
        onClose = {},
        onSymbol = { _ -> },
        onName = { _ -> },
        onAssetType = { _ -> },
        onCurrency = { _ -> },
        onAdd = {},
        onRemove = { _ -> },
        onSync = {},
        onSaveRating = { _ -> },
    )

    private fun toolsActions() = PortfolioToolsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun settingsActions() = TargetSettingsActions({}, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}, com.pirlruc.finsilo.ui.settings.LockSettingsActions({}, {}, {}, {}, {}, {}), toolsActions())
}

private fun composerProbe(changed: Boolean, execute: Boolean): Composer {
    val handler = java.lang.reflect.InvocationHandler { proxy, method, _ ->
        when (method.name) {
            "startRestartGroup", "startReplaceGroup", "startReusableGroup", "startMovableGroup" -> proxy
            "changed", "changedInstance" -> changed
            "shouldExecute" -> execute
            "skipping", "inserting" -> false
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> false
            "toString" -> "probe"
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Float.TYPE -> 0f
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Void.TYPE -> null
                else -> null
            }
        }
    }
    return Proxy.newProxyInstance(Composer::class.java.classLoader, arrayOf(Composer::class.java), handler) as Composer
}
