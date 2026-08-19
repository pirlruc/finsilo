package com.pirlruc.finsilo.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.RelativeToAverage
import com.pirlruc.finsilo.domain.model.TechnicalCross

@Composable
internal fun SignalsCard(signals: List<MarketSignal>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ratings and moving averages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Latest on-device close versus SMA 50/200. Crosses use consecutive daily SMA observations (Phase 5 rule), shown here instead of only as a notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            signals.forEach { signal -> SignalRow(signal) }
        }
    }
}

@Composable
internal fun SignalRow(signal: MarketSignal) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(signal.asset.symbol, fontWeight = FontWeight.SemiBold)
            Text(signal.rating.displayName, color = MaterialTheme.colorScheme.primary)
        }
        val parts = signalCaption(signal)
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun signalCaption(signal: MarketSignal): List<String> = buildList {
    signal.vsSma50?.let { add("vs SMA50 ${it.label()}") }
    signal.vsSma200?.let { add("vs SMA200 ${it.label()}") }
    signal.cross?.let { add(it.label()) }
    if (signal.ratingChanged) {
        add("rating ${signal.previousRating?.displayName} → ${signal.rating.displayName}")
    }
}

internal fun RelativeToAverage.label(): String = when (this) {
    RelativeToAverage.ABOVE -> "above"
    RelativeToAverage.BELOW -> "below"
}

internal fun TechnicalCross.label(): String = when (this) {
    TechnicalCross.GOLDEN -> "Golden cross"
    TechnicalCross.DEATH -> "Death cross"
}
