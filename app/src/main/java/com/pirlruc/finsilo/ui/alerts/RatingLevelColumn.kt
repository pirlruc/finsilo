package com.pirlruc.finsilo.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirlruc.finsilo.domain.model.AnalystRating

@Composable
internal fun RatingLevelColumn(selected: Set<AnalystRating>, onToggle: (AnalystRating, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Notify when the stored rating enters a checked level. Unchecking every level stores notify-none.",
            style = MaterialTheme.typography.bodySmall,
        )
        AnalystRating.entries.filter { it != AnalystRating.NONE }.forEach { rating ->
            val checked = rating in selected
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(rating, !checked) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(rating.displayName)
            }
        }
    }
}
