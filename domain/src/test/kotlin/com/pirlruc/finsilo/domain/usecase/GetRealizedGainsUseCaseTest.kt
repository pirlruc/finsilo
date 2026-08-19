package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.RealizedKind
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetRealizedGainsUseCaseTest {
    private val useCase = GetRealizedGainsUseCase()
    private val cash = Asset("cash", "EUR-CASH", "Cash", AssetType.CASH, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
    private val ct = Asset("ct", "CT", "Certificados", AssetType.CT, Currency.EUR)

    @Test
    fun twoLotsAreConsumedFifoAndPartialLotLeavesRemainder() {
        val snapshot =
            book(
                cashIn("c", LocalDate.of(2023, 1, 1), bd("10000")),
                buy("b1", etf.id, LocalDate.of(2023, 6, 1), bd("10"), bd("100"), sequence = 2),
                buy("b2", etf.id, LocalDate.of(2024, 1, 15), bd("5"), bd("120"), sequence = 3),
                sell("s1", etf.id, LocalDate.of(2025, 3, 1), bd("12"), bd("150"), fees = bd("6"), sequence = 4),
            )
        val report = useCase(snapshot, 2025)
        assertEquals(2, report.lines.size)
        assertEquals(etf.id, report.lines[0].asset.id)
        assertEquals(LocalDate.of(2023, 6, 1), report.lines[0].acquiredDate)
        assertEquals(0, bd("10").compareTo(report.lines[0].quantity))
        assertEquals(0, bd("1000").compareTo(report.lines[0].costEur))
        assertEquals(LocalDate.of(2024, 1, 15), report.lines[1].acquiredDate)
        assertEquals(0, bd("2").compareTo(report.lines[1].quantity))
        assertEquals(0, bd("240").compareTo(report.lines[1].costEur))
        assertEquals(RealizedKind.DISPOSAL, report.lines[0].kind)
        val leftover = PositionLedger().position(snapshot.transactions.filter { it.assetId == etf.id })
        assertEquals(0, bd("3").compareTo(leftover.quantity))
        assertEquals(0, report.totalGainEur.compareTo(report.lines.fold(bd("0")) { acc, line -> acc.add(line.gainEur) }))
    }

    @Test
    fun sellsInOtherYearsStillConsumeLotsForLaterReports() {
        val snapshot =
            book(
                cashIn("c", LocalDate.of(2023, 1, 1), bd("5000")),
                buy("b1", etf.id, LocalDate.of(2023, 1, 2), bd("10"), bd("100"), sequence = 2),
                sell("s-early", etf.id, LocalDate.of(2024, 6, 1), bd("4"), bd("130"), sequence = 3),
                sell("s-year", etf.id, LocalDate.of(2025, 2, 1), bd("3"), bd("140"), sequence = 4),
            )
        val in2025 = useCase(snapshot, 2025)
        assertEquals(1, in2025.lines.size)
        assertEquals(LocalDate.of(2023, 1, 2), in2025.lines.single().acquiredDate)
        assertEquals(0, bd("3").compareTo(in2025.lines.single().quantity))
        assertEquals(0, bd("300").compareTo(in2025.lines.single().costEur))
        assertTrue(useCase(snapshot, 2024).lines.isNotEmpty())
        assertTrue(useCase(snapshot, 2026).lines.isEmpty())
    }

    @Test
    fun locallyValuedRedemptionIsLabeledSeparately() {
        val snapshot =
            book(
                cashIn("c", LocalDate.of(2024, 1, 1), bd("2000")),
                buy("b", ct.id, LocalDate.of(2024, 1, 2), bd("1000"), bd("1"), sequence = 2),
                sell("s", ct.id, LocalDate.of(2025, 6, 1), bd("400"), bd("1.10"), sequence = 3),
            )
        val report = useCase(snapshot, 2025)
        assertEquals(1, report.lines.size)
        assertEquals(RealizedKind.REDEMPTION, report.lines.single().kind)
        assertEquals(ct.id, report.lines.single().asset.id)
        assertTrue(report.lines.single().gainEur.signum() > 0)
    }

    @Test
    fun unknownAssetAndEmptyLotsEmitNoLines() {
        val orphan =
            book(
                cashIn("c", LocalDate.of(2024, 1, 1), bd("100")),
                sell("ghost", "missing", LocalDate.of(2025, 1, 1), bd("1"), bd("10"), sequence = 2),
            )
        assertTrue(useCase(orphan, 2025).lines.isEmpty())
        val noLots =
            book(
                cashIn("c", LocalDate.of(2024, 1, 1), bd("100")),
                sell("s", etf.id, LocalDate.of(2025, 1, 1), bd("1"), bd("10"), sequence = 2),
            )
        assertTrue(useCase(noLots, 2025).lines.isEmpty())
    }

    @Test
    fun sameDayLotsFollowSequenceNotUuidOrder() {
        val snapshot =
            book(
                cashIn("c", LocalDate.of(2025, 1, 1), bd("5000")),
                buy("zzz", etf.id, LocalDate.of(2025, 3, 1), bd("1"), bd("10"), sequence = 2),
                buy("aaa", etf.id, LocalDate.of(2025, 3, 1), bd("1"), bd("90"), sequence = 3),
                sell("s", etf.id, LocalDate.of(2025, 3, 2), bd("1"), bd("100"), sequence = 4),
            )
        val line = useCase(snapshot, 2025).lines.single()
        assertEquals("zzz", snapshot.transactions.single { it.sequence == 2L }.id)
        assertEquals(0, bd("10").compareTo(line.costEur))
        assertEquals(LocalDate.of(2025, 3, 1), line.acquiredDate)
    }

    @Test
    fun dividendsAndInterestDoNotCreateLotLines() {
        val snapshot =
            book(
                cashIn("c", LocalDate.of(2025, 1, 1), bd("2000")),
                buy("b", etf.id, LocalDate.of(2025, 1, 2), bd("2"), bd("50"), sequence = 2),
                Transaction(
                    id = "d",
                    assetId = etf.id,
                    date = LocalDate.of(2025, 6, 1),
                    type = TransactionType.DIVIDEND,
                    quantity = BigDecimal.ONE,
                    unitPriceNative = bd("1"),
                    exchangeRateAtExecution = BigDecimal.ONE,
                    unitPriceEur = bd("1"),
                    feesEur = BigDecimal.ZERO,
                    sequence = 3,
                ),
            )
        assertTrue(useCase(snapshot, 2025).lines.isEmpty())
    }

    private fun book(vararg txs: Transaction): PortfolioSnapshot {
        val assets = listOf(cash, etf, ct)
        return PortfolioSnapshot(assets, txs.toList(), emptyList(), emptyList(), emptyList())
    }

    private fun cashIn(id: String, date: LocalDate, amount: BigDecimal) = Transaction(
        id,
        cash.id,
        date,
        TransactionType.DEPOSIT_CASH,
        amount,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        sequence = 1,
    )

    private fun buy(
        id: String,
        assetId: String,
        date: LocalDate,
        qty: BigDecimal,
        price: BigDecimal,
        sequence: Long,
    ) = Transaction(
        id,
        assetId,
        date,
        TransactionType.BUY,
        qty,
        price,
        BigDecimal.ONE,
        price,
        BigDecimal.ZERO,
        sequence = sequence,
    )

    private fun sell(
        id: String,
        assetId: String,
        date: LocalDate,
        qty: BigDecimal,
        price: BigDecimal,
        fees: BigDecimal = BigDecimal.ZERO,
        sequence: Long,
    ) = Transaction(
        id,
        assetId,
        date,
        TransactionType.SELL,
        qty,
        price,
        BigDecimal.ONE,
        price,
        fees,
        sequence = sequence,
    )
}
