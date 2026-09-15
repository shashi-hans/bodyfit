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
 */
@Composable
fun Glyph(
    emoji: String,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
) {
    if (iconRes != null) {
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
