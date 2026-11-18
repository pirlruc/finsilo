package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Read-only IBKR Flex, Kraken, and Coinbase statements mapped to ledger rows. */
object ReadOnlyBrokers {
    private val json = Json { ignoreUnknownKeys = true }

    fun ibkrAllowed(method: String, url: String): Boolean = allowed(
        method,
        url,
        "GET",
        "gdcdyn.interactivebrokers.com",
        setOf(
            "/Universal/servlet/FlexStatementService.SendRequest",
            "/Universal/servlet/FlexStatementService.GetStatement",
        ),
    )

    fun krakenAllowed(method: String, url: String): Boolean = allowed(
        method,
        url,
        "POST",
        "api.kraken.com",
        setOf("/0/private/Ledgers", "/0/private/TradesHistory"),
    )

    fun coinbaseAllowed(method: String, url: String): Boolean = allowed(
        method,
        url,
        "GET",
        "api.coinbase.com",
        setOf("/api/v3/brokerage/orders/historical/fills"),
    )

    fun krakenSignature(path: String, nonce: String, postData: String, secret: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((nonce + postData).toByteArray(StandardCharsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret, "HmacSHA512"))
        mac.update(path.toByteArray(StandardCharsets.UTF_8))
        mac.update(digest)
        return Base64.getEncoder().encodeToString(mac.doFinal())
    }

    fun coinbaseJwt(keyName: String, privateKey: PrivateKey, nowSeconds: Long, nonce: String): String {
        val header = """{"alg":"ES256","kid":"$keyName","nonce":"$nonce","typ":"JWT"}"""
        val uri = "GET api.coinbase.com/api/v3/brokerage/orders/historical/fills"
        val payload = """{"sub":"$keyName","iss":"cdp","nbf":$nowSeconds,"exp":${nowSeconds + 120},"uri":"$uri"}"""
        val signing = base64Url(header.toByteArray()) + "." + base64Url(payload.toByteArray())
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(signing.toByteArray(StandardCharsets.UTF_8))
        return signing + "." + base64Url(derToP1363(signature.sign()))
    }

    fun ibkrTrades(xml: String): List<BrokerCsvLine> {
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) return emptyList()
        return tradeTags(xml).mapIndexed { index, tag -> ibkrTrade(tag, index + 1) }
    }

    fun krakenLedgers(body: String): List<BrokerCsvLine> = jsonEntries(body, "result").mapIndexed { index, entry ->
        val raw = entry.second["amount"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val side = if (raw.signum() < 0) TransactionType.SELL else TransactionType.BUY
        line(BrokerCsvFormat.KRAKEN, index + 1, epochDate(entry.second["time"]), side, entry.second["asset"].orEmpty(), raw.abs(), entry.first)
    }

    fun coinbaseFills(body: String): List<BrokerCsvLine> {
        val fills = runCatching { json.parseToJsonElement(body).jsonObject["fills"]?.jsonArray }.getOrNull() ?: return emptyList()
        return fills.mapIndexed { index, element ->
            val row = element.jsonObject
            val side = if (row["side"]?.jsonPrimitive?.content == "SELL") TransactionType.SELL else TransactionType.BUY
            val product = row["product_id"]?.jsonPrimitive?.content.orEmpty()
            line(
                BrokerCsvFormat.COINBASE,
                index + 1,
                row["trade_time"]?.jsonPrimitive?.content?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                side,
                product,
                decimal(row["size"]?.jsonPrimitive?.content),
                row["entry_id"]?.jsonPrimitive?.content,
            )
        }
    }

    private fun ibkrTrade(tag: String, index: Int): BrokerCsvLine {
        val side = if (attr(tag, "buySell").equals("SELL", ignoreCase = true)) TransactionType.SELL else TransactionType.BUY
        val date = attr(tag, "tradeDate").let { raw ->
            if (raw.length == 8) runCatching { LocalDate.of(raw.take(4).toInt(), raw.substring(4, 6).toInt(), raw.takeLast(2).toInt()) }.getOrNull() else null
        }
        return line(BrokerCsvFormat.IBKR_FLEX, index, date, side, attr(tag, "symbol"), decimal(attr(tag, "quantity")), attr(tag, "tradeID"))
    }

    private fun line(
        format: BrokerCsvFormat,
        index: Int,
        date: LocalDate?,
        type: TransactionType,
        symbol: String,
        quantity: BigDecimal,
        reference: String?,
    ): BrokerCsvLine = BrokerCsvLine(
        date = date,
        type = type,
        skipReason = null,
        symbol = symbol,
        name = symbol,
        isin = null,
        quoteSymbol = symbol,
        assetType = AssetType.STOCK,
        quantity = quantity,
        unitPriceNative = BigDecimal.ZERO,
        currency = Currency.EUR,
        feesEur = BigDecimal.ZERO,
        eurPerUsd = null,
        sourceLine = index,
        format = format,
        brokerReference = reference,
    )

    private fun jsonEntries(body: String, root: String): List<Pair<String, Map<String, String?>>> {
        val objectNode = runCatching { json.parseToJsonElement(body).jsonObject[root]?.jsonObject }.getOrNull() ?: return emptyList()
        val ledger = objectNode["ledger"]?.jsonObject ?: objectNode
        return ledger.map { (id, value) ->
            val fields = value.jsonObject.mapValues { it.value.jsonPrimitive.content }
            id to fields
        }
    }

    private fun tradeTags(xml: String): List<String> {
        val tags = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = xml.indexOf("<Trade", from)
            if (start < 0) break
            val end = xml.indexOf('>', start)
            if (end < 0) break
            tags += xml.substring(start, end)
            from = end + 1
        }
        return tags
    }

    private fun attr(tag: String, name: String): String {
        val marker = "$name=\""
        val start = tag.indexOf(marker)
        if (start < 0) return ""
        val value = start + marker.length
        val end = tag.indexOf('"', value)
        return if (end < 0) "" else tag.substring(value, end)
    }

    private fun allowed(method: String, url: String, expected: String, host: String, paths: Set<String>): Boolean {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        return method == expected && parsed.scheme == "https" && parsed.host == host && parsed.rawPath in paths && parsed.userInfo == null
    }

    private fun decimal(raw: String?): BigDecimal = raw?.toBigDecimalOrNull()?.abs() ?: BigDecimal.ZERO

    private fun epochDate(raw: String?): LocalDate? {
        val seconds = raw?.substringBefore('.')?.toLongOrNull() ?: return null
        return runCatching { java.time.Instant.ofEpochSecond(seconds).atZone(java.time.ZoneOffset.UTC).toLocalDate() }.getOrNull()
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun derToP1363(der: ByteArray): ByteArray {
        val out = ByteArray(P1363)
        var index = 2
        repeat(2) { half ->
            if (der[index] != INT) return der
            val length = der[index + 1].toInt() and 0xff
            val start = index + 2
            val raw = der.copyOfRange(start, start + length).dropWhile { it == 0.toByte() }
            val padded = ByteArray(COORD)
            val copy = raw.takeLast(COORD)
            copy.forEachIndexed { offset, byte -> padded[COORD - copy.size + offset] = byte }
            padded.copyInto(out, half * COORD)
            index = start + length
        }
        return out
    }

    private const val INT: Byte = 0x02
    private const val COORD: Int = 32
    private const val P1363: Int = 64
}
