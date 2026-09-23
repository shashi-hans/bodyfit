package app.bodyfit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mark that identifies a metric: a drawable where one exists, otherwise its emoji.
 *
 * Most metrics ride on an emoji, which costs nothing and matches the system font. Steps
 * has a real icon because the shoe emoji reads differently on every launcher skin, and
 * because the same drawable has to serve the notification's status-bar icon, where an
 * emoji is not an option.
 *
 * Somewhere the mark has to carry the metric's colour, a vector is passed instead: an
 * emoji paints itself and ignores any tint.
 */
@Composable
fun Glyph(
    emoji: String,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
    /** Preferred over both when present, because a vector takes [tint] and an emoji cannot. */
    vector: ImageVector? = null,
) {
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
    } else if (iconRes != null) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
    } else {
        Text(text = emoji, style = MaterialTheme.typography.labelLarge, modifier = modifier)
    }
}
