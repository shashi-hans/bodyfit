package app.bodyfit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.data.WaterEntry
import app.bodyfit.ui.components.SectionHeader
import app.bodyfit.ui.components.WaterGlass
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WaterScreen(
    record: DailyRecord,
    entries: List<WaterEntry>,
    settings: UserSettings,
    onLogWater: (Int) -> Unit,
    onUndoWater: (WaterEntry) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val goal = settings.waterGoalMl
    val progress = if (goal > 0) record.waterMl.toFloat() / goal else 0f
    val remaining = (goal - record.waterMl).coerceAtLeast(0)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WaterGlass(progress = progress)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "${Volume.format(record.waterMl)} of ${Volume.format(goal)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (remaining == 0) {
                            "🎉 Hydration goal reached"
                        } else {
                            "💧 ${Volume.format(remaining)} to go"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item { SectionHeader(emoji = "🥤", title = "Add a drink") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UserSettings.CUP_SIZES_ML.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { amount ->
                            ElevatedAssistChip(
                                onClick = { onLogWater(amount) },
                                label = { Text("+$amount ml") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                emoji = "📝",
                title = "Today's log",
                trailing = "${entries.size} ${if (entries.size == 1) "drink" else "drinks"}",
            )
        }

        if (entries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Nothing logged yet. Tap a size above, or use the buttons on the lock-screen card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            WaterRow(entry = entry, onUndo = { onUndoWater(entry) })
                            if (index != entries.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun WaterRow(entry: WaterEntry, onUndo: () -> Unit) {
    val time = remember(entry.loggedAt) {
        Instant.ofEpochMilli(entry.loggedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "💧 ${Volume.format(entry.amountMl)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUndo) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove this ${entry.amountMl} ml entry",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
