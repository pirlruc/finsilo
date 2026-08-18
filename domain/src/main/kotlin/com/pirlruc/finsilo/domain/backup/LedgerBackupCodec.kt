package com.pirlruc.finsilo.domain.backup

import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Outcome of opening an on-device backup file. */
sealed class LedgerBackupResult {
    /** Decrypted ledger. */
    data class Restored(val snapshot: PortfolioSnapshot) : LedgerBackupResult()

    /** Truncated, corrupt, or wrong passphrase. */
    data class Refused(val reason: String) : LedgerBackupResult()
}

/**
 * Encrypted portable snapshot. Not plaintext SQLite.
 *
 * Layout: magic `FSILO1` + 16-byte salt + 12-byte IV + AES-256-GCM ciphertext of a
 * tab-separated ledger. The wrapping key is PBKDF2 of the recovery code the user
 * typed at export, so a new phone can restore after unlock.
 */
object LedgerBackupCodec {
    private val magic = byteArrayOf(0x46, 0x53, 0x49, 0x4C, 0x4F, 0x31)
    private const val IV_BYTES: Int = 12
    private const val TAG_BITS: Int = 128
    private const val MIN_SIZE: Int = 6 + AppLockCrypto.SALT_BYTES + IV_BYTES + 16

    /** Encrypt [snapshot] with a recovery-code passphrase. */
    fun encrypt(snapshot: PortfolioSnapshot, passphrase: String, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = AppLockCrypto.generateSalt(random)
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(LedgerBackupText.encode(snapshot).toByteArray(Charsets.UTF_8))
        return magic + salt + iv + body
    }

    /** Decrypt [bytes]; truncated or corrupt files are [LedgerBackupResult.Refused]. */
    fun decrypt(bytes: ByteArray, passphrase: String): LedgerBackupResult {
        if (bytes.size < MIN_SIZE) return LedgerBackupResult.Refused("Backup file is truncated.")
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            return LedgerBackupResult.Refused("Not a FinSilo backup.")
        }
        val saltEnd = magic.size + AppLockCrypto.SALT_BYTES
        val ivEnd = saltEnd + IV_BYTES
        val salt = bytes.copyOfRange(magic.size, saltEnd)
        val iv = bytes.copyOfRange(saltEnd, ivEnd)
        val body = bytes.copyOfRange(ivEnd, bytes.size)
        return openBody(body, passphrase, salt, iv)
    }

    private fun openBody(body: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): LedgerBackupResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val plain =
            try {
                cipher.doFinal(body).toString(Charsets.UTF_8)
            } catch (_: AEADBadTagException) {
                return LedgerBackupResult.Refused("Wrong recovery code or corrupt backup.")
            } catch (_: Exception) {
                return LedgerBackupResult.Refused("Could not decrypt backup.")
            }
        return LedgerBackupText.decode(plain)
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val secret = AppLockCrypto.hashSecret(AppLockCrypto.normalizeRecovery(passphrase), salt)
        return SecretKeySpec(secret, "AES")
    }
}

/** Tab-separated snapshot text inside the ciphertext. */
object LedgerBackupText {
    /** Encode [snapshot] as versioned TSV. */
    fun encode(snapshot: PortfolioSnapshot): String {
        val lines = ArrayList<String>()
        lines += "FSILO-LEDGER-1"
        snapshot.assets.forEach { asset ->
            lines +=
                listOf(
                    "A",
                    asset.id,
                    esc(asset.symbol),
                    esc(asset.name),
                    asset.assetType.name,
                    asset.baseCurrency.name,
                    esc(asset.isin.orEmpty()),
                    esc(asset.quoteSymbol.orEmpty()),
                ).joinToString("\t")
        }
        snapshot.transactions.forEach { tx ->
            lines +=
                listOf(
                    "T",
                    tx.id,
                    tx.assetId,
                    tx.date.toString(),
                    tx.type.name,
                    tx.quantity.toPlainString(),
                    tx.unitPriceNative.toPlainString(),
                    tx.exchangeRateAtExecution.toPlainString(),
                    tx.unitPriceEur.toPlainString(),
                    tx.feesEur.toPlainString(),
                    tx.sequence.toString(),
                ).joinToString("\t")
        }
        snapshot.marketData.forEach { row ->
            lines +=
                listOf(
                    "M",
                    row.assetId,
                    row.date.toString(),
                    row.closingPriceNative.toPlainString(),
                    row.analystRating.name,
                    row.sma50?.toPlainString().orEmpty(),
                    row.sma200?.toPlainString().orEmpty(),
                ).joinToString("\t")
        }
        snapshot.fxRates.forEach { rate ->
            lines += listOf("X", rate.date.toString(), rate.eurPerUsd.toPlainString()).joinToString("\t")
        }
        snapshot.targets.forEach { target ->
            lines += listOf("G", target.assetType.name, target.weightPercent.toPlainString()).joinToString("\t")
        }
        lines += "END"
        return lines.joinToString("\n")
    }

    /** Parse plaintext; refuse unknown versions and malformed rows. */
    fun decode(text: String): LedgerBackupResult {
        val lines = text.split('\n')
        if (lines.firstOrNull() != "FSILO-LEDGER-1") {
            return LedgerBackupResult.Refused("Unknown backup format.")
        }
        return runCatching { parseLines(lines.drop(1)) }
            .getOrElse { LedgerBackupResult.Refused("Backup payload is malformed.") }
    }

    private fun parseLines(lines: List<String>): LedgerBackupResult {
        val assets = ArrayList<Asset>()
        val txs = ArrayList<Transaction>()
        val market = ArrayList<DailyMarketData>()
        val fx = ArrayList<CurrencyRate>()
        val targets = ArrayList<TargetAllocation>()
        val unknown = lines.firstOrNull { row -> unknownRow(row) }
        if (unknown != null) return LedgerBackupResult.Refused("Unknown backup row.")
        lines.filter { it.isNotEmpty() && it != "END" }.forEach { line ->
            appendRow(line.split('\t'), assets, txs, market, fx, targets)
        }
        return LedgerBackupResult.Restored(PortfolioSnapshot(assets, txs, market, fx, targets))
    }

    private fun unknownRow(line: String): Boolean {
        if (line.isEmpty() || line == "END") return false
        return line.substringBefore('\t') !in setOf("A", "T", "M", "X", "G")
    }

    private fun appendRow(
        cols: List<String>,
        assets: ArrayList<Asset>,
        txs: ArrayList<Transaction>,
        market: ArrayList<DailyMarketData>,
        fx: ArrayList<CurrencyRate>,
        targets: ArrayList<TargetAllocation>,
    ) {
        when (cols.firstOrNull()) {
            "A" -> assets += parseAsset(cols)
            "T" -> txs += parseTx(cols)
            "M" -> market += parseMarket(cols)
            "X" -> fx += parseFx(cols)
            "G" -> targets += parseTarget(cols)
        }
    }

    private fun parseAsset(cols: List<String>): Asset {
        require(cols.size >= 8)
        return Asset(
            id = cols[1],
            symbol = unesc(cols[2]),
            name = unesc(cols[3]),
            assetType = AssetType.valueOf(cols[4]),
            baseCurrency = Currency.valueOf(cols[5]),
            isin = unesc(cols[6]).ifBlank { null },
            quoteSymbol = unesc(cols[7]).ifBlank { null },
        )
    }

    private fun parseTx(cols: List<String>): Transaction {
        require(cols.size >= 11)
        return Transaction(
            id = cols[1],
            assetId = cols[2],
            date = LocalDate.parse(cols[3]),
            type = TransactionType.valueOf(cols[4]),
            quantity = BigDecimal(cols[5]),
            unitPriceNative = BigDecimal(cols[6]),
            exchangeRateAtExecution = BigDecimal(cols[7]),
            unitPriceEur = BigDecimal(cols[8]),
            feesEur = BigDecimal(cols[9]),
            sequence = cols[10].toLong(),
        )
    }

    private fun parseMarket(cols: List<String>): DailyMarketData {
        require(cols.size >= 7)
        return DailyMarketData(
            assetId = cols[1],
            date = LocalDate.parse(cols[2]),
            closingPriceNative = BigDecimal(cols[3]),
            analystRating = AnalystRating.valueOf(cols[4]),
            sma50 = cols[5].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
            sma200 = cols[6].takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
        )
    }

    private fun parseFx(cols: List<String>): CurrencyRate {
        require(cols.size >= 3)
        return CurrencyRate(LocalDate.parse(cols[1]), BigDecimal(cols[2]))
    }

    private fun parseTarget(cols: List<String>): TargetAllocation {
        require(cols.size >= 3)
        return TargetAllocation(AssetType.valueOf(cols[1]), BigDecimal(cols[2]))
    }

    private fun esc(value: String): String = value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(value: String): String = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
