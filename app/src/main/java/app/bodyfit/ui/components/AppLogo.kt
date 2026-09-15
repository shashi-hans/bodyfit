package app.bodyfit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bodyfit.R

/**
 * The app's wordmark: the launcher glyph in its own tile, then the name in two tones.
 *
 * The tile reuses the launcher icon drawable and its background color, so the mark in the
 * app is the same mark on the home screen rather than a lookalike. "Body" wears text ink
 * and "Fit" wears the brand green; the glow behind them is wide and barely offset, which
 * reads as depth instead of as a displaced second copy of the letters.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        )
        Spacer(Modifier.width(9.dp))

        val wordmark = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.6).sp,
            shadow = Shadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                offset = Offset(0f, 2f),
                blurRadius = 18f,
            ),
        )
        Text(text = "Body", style = wordmark, color = MaterialTheme.colorScheme.onSurface)
        Text(text = "Fit", style = wordmark, color = MaterialTheme.colorScheme.primary)
    }
}
