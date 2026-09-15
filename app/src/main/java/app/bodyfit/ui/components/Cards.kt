package app.bodyfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A single measurement.
 *
 * The emoji and the label carry identity; the value wears text ink, never the metric
 * hue. The optional [accent] tints only the meter, so several of these can sit side by
 * side without turning the screen into a color chart.
 */
@Composable
fun StatCard(
    emoji: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    /** Blank for a unitless figure such as BMI, which then renders without a trailing gap. */
    unit: String = "",
    progress: Float? = null,
    accent: Color? = null,
    caption: String? = null,
    /** Used for a status word such as "Low risk". The word stays, so colour is never alone. */
    captionColor: Color? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "  $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            if (progress != null && accent != null) {
                Spacer(Modifier.height(10.dp))
                ProgressMeter(progress = progress, color = accent, modifier = Modifier.fillMaxWidth())
            }
            if (caption != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = captionColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Section heading with an emoji, used to break the long scrolling screens into parts. */
@Composable
fun SectionHeader(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$emoji  $title",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
