package com.pirlruc.finsilo

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pirlruc.finsilo.data.security.AppLockStore
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.entitlement.FlagEntitlements
import com.pirlruc.finsilo.domain.entitlement.PremiumFeature
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.vault.VaultRecord
import com.pirlruc.finsilo.domain.vault.VaultRegistry
import com.pirlruc.finsilo.domain.vault.VaultSelection
import com.pirlruc.finsilo.ui.dashboard.PremiumInsightCards
import com.pirlruc.finsilo.ui.settings.Trading212SettingsCard
import com.pirlruc.finsilo.ui.settings.VaultSwitcherCard
import com.pirlruc.finsilo.ui.theme.FinsiloTheme
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Wave4AppTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun bothPremiumFlagValuesAreCoveredAndTheDebugBuildStaysUnlocked() {
        assertTrue(BuildConfig.PREMIUM_UNLOCKED)
        assertTrue(FlagEntitlements(true).allows(PremiumFeature.REBALANCE))
        assertTrue(FlagEntitlements(BuildConfig.PREMIUM_UNLOCKED).allows(PremiumFeature.MULTI_PORTFOLIO))
        assertFalse(FlagEntitlements(false).allows(PremiumFeature.FIRE_DRIP))
        assertFalse(FlagEntitlements(false).allows(PremiumFeature.REBALANCE))
    }

    @Test
    fun secondVaultKeepsItsOwnPinAndDoesNotMoveTheLegacyFile() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("wave4-vault", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val personal = DatabaseKeyStore(prefs)
        val familySelection = VaultSelection("family")
        val family = DatabaseKeyStore(prefs, familySelection)
        val familyLock = AppLockStore(prefs, familySelection)
        assertTrue(personal.provision("1234", "ABCD-1234-EFGH-5678"))
        assertTrue(family.provision("5678", "ZZZZ-9999-YYYY-8888"))
        assertTrue(familyLock.setup("5678", "ZZZZ-9999-YYYY-8888", false))
        assertTrue(prefs.contains("sqlcipher_wrap_pin"))
        assertTrue(prefs.contains("vault.family.sqlcipher_wrap_pin"))
        assertFalse(prefs.getString("sqlcipher_wrap_pin", "") == prefs.getString("vault.family.sqlcipher_wrap_pin", "x"))
        val reopened = DatabaseKeyStore(prefs, familySelection)
        assertTrue(reopened.unlockWithPin("5678"))
        assertFalse(reopened.unlockWithPin("1234"))
        val dir = folder.newFolder("databases")
        val legacy = File(dir, "finsilo.db")
        legacy.writeBytes(byteArrayOf(9, 8, 7))
        val created = VaultDirectory(dir).create("Family")
        assertEquals("finsilo-family.db", created!!.fileName)
        assertTrue(legacy.readBytes().contentEquals(byteArrayOf(9, 8, 7)))
        assertTrue(File(dir, VaultRegistry.REGISTRY_FILE).isFile)
        assertFalse(File(dir, "finsilo-family.db").exists())
        assertEquals(VaultRegistry.LEGACY_FILE, VaultRegistry.fileName(VaultSelection().id))
    }

    @Test
    fun trading212StatusIsShownAndSecretsAreNotSaveableState() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("wave4-t212", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val keys = DatabaseKeyStore(prefs)
        val message = "Trading 212 rejected the API key or secret (HTTP 401)."
        assertTrue(keys.setTrading212Status(message))
        assertEquals(message, keys.trading212Status())
        val worker = File("src/main/java/com/pirlruc/finsilo/data/sync/DailyMarketSyncWorker.kt").readText()
        assertTrue(worker.contains("setTrading212Status"))
        val card = File("src/main/java/com/pirlruc/finsilo/ui/settings/Trading212SettingsCard.kt").readText()
        assertFalse(card.contains("rememberSaveable"))
        val dividends = listOf(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 4, 15), LocalDate.of(2024, 7, 15)).map { date ->
            Transaction(
                id = date.toString(),
                assetId = "a",
                date = date,
                type = TransactionType.DIVIDEND,
                quantity = BigDecimal.ONE,
                unitPriceNative = BigDecimal.ONE,
                exchangeRateAtExecution = BigDecimal.ONE,
                unitPriceEur = BigDecimal("2"),
                feesEur = BigDecimal.ZERO,
            )
        }
        compose.setContent {
            FinsiloTheme {
                Trading212SettingsCard(keys)
                PremiumInsightCards(emptyList(), BigDecimal.TEN, dividends, LocalDate.of(2024, 8, 1), FlagEntitlements(true))
                PremiumInsightCards(emptyList(), BigDecimal.TEN, emptyList(), LocalDate.of(2024, 1, 1), FlagEntitlements(false))
                VaultSwitcherCard(
                    listOf(
                        VaultRecord("default", "finsilo.db", "Personal"),
                        VaultRecord("family", "finsilo-family.db", "Family"),
                    ),
                    "default",
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText(message).assertExists()
        compose.onNodeWithText("Rebalance simulator").assertExists()
        compose.onNodeWithText("Not financial advice.").assertExists()
        compose.onNodeWithText("Vaults").assertExists()
        compose.onNodeWithText("Buys and sells").assertExists()
        compose.onNodeWithText("Family").performClick()
        compose.onNodeWithText("Add vault").performClick()
        compose.onNodeWithText("API key").performTextInput("key")
        compose.onNodeWithText("API secret").performTextInput("secret")
        compose.onNodeWithText("Save Trading 212").performClick()
    }
}
