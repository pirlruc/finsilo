package com.pirlruc.finsilo.domain.market

import com.pirlruc.finsilo.domain.model.Currency
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParserEdgeTest {
    @Test
    fun frankfurterReadsRatesObjectsAndSkipsJunk() {
        assertNull(FrankfurterParser.eurPerUsd("nope"))
        assertNull(FrankfurterParser.eurPerUsd("""{"EUR":null}"""))
        assertEquals(0, BigDecimal("0.91").compareTo(FrankfurterParser.eurPerUsd("""{"EUR":0.91}""")))
        assertEquals(0, BigDecimal("0.92").compareTo(FrankfurterParser.eurPerUsd("""{"rates":{"EUR":0.92}}""")))
        assertNull(FrankfurterParser.eurPerUsd("""{"rates":[]}"""))

        val crosses = FrankfurterParser.latestCrosses(
            """{"rates":{"USD":0.90,"XXX":1,"GBP":"nope","CHF":null}}""",
        )
        assertEquals(setOf(Currency.USD), crosses.keys)

        val series = FrankfurterParser.eurPerUsdSeries(
            """{"2024-01-02":{"GBP":0.85},"bad":{"USD":1},"2024-01-01":"x","2024-01-03":{}}""",
        )
        assertEquals(1, series.size)
        assertEquals(Currency.GBP, series.single().perUnit.keys.single())
        assertTrue(FrankfurterParser.eurPerUsdSeries("[]").isEmpty())
        assertTrue(FrankfurterParser.latestCrosses("[]").isEmpty())
        assertTrue(FrankfurterParser.latestCrosses("""{"rates":[1],"USD":0.5}""").containsKey(Currency.USD))
        val bare = FrankfurterParser.eurPerUsdSeries("""{"2024-01-01":{"USD":0.9,"XXX":1}}""")
        assertEquals(1, bare.size)
        assertTrue(FrankfurterParser.eurPerUsdSeries("""{"rates":[1]}""").isEmpty())
    }

    @Test
    fun coinGeckoSkipsShortPairs() {
        val closes = CoinGeckoParser.dailyCloses("""{"prices":[[1],[1755302400000,1.5]]}""")
        assertEquals(1, closes.size)
        assertTrue(CoinGeckoParser.dailyCloses("[]").isEmpty())
    }
}
