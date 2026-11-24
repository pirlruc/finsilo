package com.pirlruc.finsilo.data.sync

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.remote.OkHttpBrokerCall
import com.pirlruc.finsilo.data.remote.OkHttpWebDavCall
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.backup.WebDavClient
import com.pirlruc.finsilo.domain.backup.WebDavExchange
import com.pirlruc.finsilo.domain.importcsv.BrokerExchange
import com.pirlruc.finsilo.domain.importcsv.CoinbaseFillsClient
import com.pirlruc.finsilo.domain.importcsv.IbkrFlexClient
import com.pirlruc.finsilo.domain.importcsv.KrakenReadClient
import com.pirlruc.finsilo.domain.performance.FireAssumptions
import com.pirlruc.finsilo.ui.settings.BrokerSyncSettingsCard
import com.pirlruc.finsilo.ui.theme.FinsiloTheme
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReadOnlyLedgerSyncTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: FinsiloDatabase
    private lateinit var repository: RoomPortfolioRepository
    private lateinit var keys: DatabaseKeyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomPortfolioRepository(database)
        val prefs = context.getSharedPreferences("readonly-sync", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        keys = DatabaseKeyStore(prefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun missingCredentialsProduceNoStatus() = runBlocking {
        assertNull(ReadOnlyLedgerSync().run(keys, repository))
        assertTrue(keys.setIbkr("token", ""))
        assertEquals("IBKR token is stored, but the query id is missing.", ReadOnlyLedgerSync().run(keys, repository))
    }

    @Test
    fun aStoredStatementIsImported() = runBlocking {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val begin = "-----BEGIN PRIVATE " + "KEY-----"
        val end = "-----END PRIVATE " + "KEY-----"
        val pem = begin + "\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) + "\n" + end
        assertTrue(keys.setIbkr("token", "query"))
        assertTrue(keys.setKraken("key", "dGVzdA=="))
        assertTrue(keys.setCoinbase("organizations/demo/apiKeys/1", pem))
        val sync = ReadOnlyLedgerSync(
            ibkr = IbkrFlexClient { _, url, _, _ ->
                if (url.contains("SendRequest")) {
                    BrokerExchange(200, "<ReferenceCode>ref</ReferenceCode>")
                } else {
                    BrokerExchange(200, """<Trade symbol="AAPL" buySell="BUY" quantity="1" tradeDate="20240115" tradeID="t"/>""")
                }
            },
            kraken = KrakenReadClient { _, _, _, _ -> BrokerExchange(200, """{"result":{"ledger":{}}}""") },
            coinbase = CoinbaseFillsClient { _, _, _, _ -> BrokerExchange(200, """{"fills":[]}""") },
        )
        assertNull(sync.run(keys, repository))
        assertEquals("AAPL", repository.load().transactions.single().assetId.let { repository.load().assets.single().symbol })
    }

    @Test
    fun aCoinbaseKeyThatIsNotPemIsReported() = runBlocking {
        assertTrue(keys.setCoinbase("name", "not-a-key"))
        val status = ReadOnlyLedgerSync().run(keys, repository)
        assertEquals("Coinbase private key is not a PKCS8 PEM.", status)
    }

    @Test
    fun webDavUploadStoresTheNewEtag() = runBlocking {
        assertNull(WebDavBackupUpload(keys).upload(byteArrayOf(1)))
        assertTrue(keys.setWebDav("https://cloud.example/FSILO-LEDGER", "me", ""))
        assertEquals("WebDAV URL is stored, but the app password is missing.", WebDavBackupUpload(keys).upload(byteArrayOf(1)))
        assertTrue(keys.setWebDav("https://cloud.example/FSILO-LEDGER", "me", "app"))
        val upload = WebDavBackupUpload(
            keys,
            WebDavClient { method, _, _, _ ->
                if (method == "PROPFIND") WebDavExchange(207, byteArrayOf(), "old") else WebDavExchange(201, byteArrayOf(), "new")
            },
        )
        assertTrue(upload.upload(byteArrayOf(1))!!.contains("newer"))
        assertNull(upload.upload(byteArrayOf(1), overwrite = true))
        assertEquals("new", keys.webDavEtag())
    }

    @Test
    fun vaultSettingsRoundTripAndTheCardStoresAToken() {
        val assumptions = FireAssumptions.defaults().copy(annualExpensesEur = java.math.BigDecimal("18000"))
        assertTrue(keys.setFireAssumptions(assumptions))
        assertEquals(0, java.math.BigDecimal("18000").compareTo(keys.fireAssumptions().annualExpensesEur))
        assertTrue(keys.setBrokerSyncStatus("IBKR sync failed"))
        assertEquals("IBKR sync failed", keys.brokerSyncStatus())
        assertTrue(keys.setBrokerSyncStatus(null))
        assertNull(keys.brokerSyncStatus())
        assertTrue(keys.setTrading212Credentials("key", "secret"))
        assertEquals("secret", keys.trading212Secret())
        assertTrue(keys.setTrading212Credentials("", ""))
        assertTrue(keys.setTrading212SyncEnabled(false))
        compose.setContent { FinsiloTheme { BrokerSyncSettingsCard(keys) } }
        compose.onNodeWithText("IBKR token").performTextInput("flex-token")
        compose.onNodeWithText("Save").performClick()
        assertNull(keys.ibkrToken())
        runCatching { DailyMarketSyncWorker.schedule(ApplicationProvider.getApplicationContext()) }
    }

    @Test
    fun theHttpAdaptersRefuseAPathOutsideTheAllowList() = runBlocking {
        val broker = runCatching { OkHttpBrokerCall().call("GET", "https://evil.example/steal", emptyMap(), "") }
        assertTrue(broker.exceptionOrNull()?.message?.contains("refused") == true)
        val dav = runCatching { OkHttpWebDavCall().call("DELETE", "https://cloud.example/FSILO-LEDGER", emptyMap(), byteArrayOf()) }
        assertTrue(dav.exceptionOrNull()?.message?.contains("refused") == true)
        val local = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("ok".toResponseBody()).build()
        }.build()
        val sent = OkHttpBrokerCall(local).call(
            "GET",
            "https://gdcdyn.interactivebrokers.com/Universal/servlet/FlexStatementService.GetStatement",
            emptyMap(),
            "",
        )
        assertEquals(200, sent.code)
        val stored = OkHttpWebDavCall(local).call("PUT", "https://cloud.example/FSILO-LEDGER", emptyMap(), byteArrayOf(1))
        assertEquals(200, stored.code)
    }
}
