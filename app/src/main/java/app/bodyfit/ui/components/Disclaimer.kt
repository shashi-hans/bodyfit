package app.bodyfit.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What every figure in this app is, and what it is not.
 *
 * One wording, held in one place and used on every screen that has to state where its
 * numbers stand. Separate copies drift: a page saying "estimate" beside one saying
 * "measured" tells the user the app disagrees with itself about its own accuracy.
 *
 * The claim is deliberate and narrow. The app reads a step sensor and, where the user
 * allows it, GPS speed; everything else, calories and heart points and resting burn and
 * stride length, is derived from those through published population formulas. That makes
 * each figure an estimate for a body of the user's build, not a measurement of the user.
 * Saying so is both honest and what a health app on a store listing has to do.
 */
object Wellness {

    /** The full statement, for a page with room to carry it. */
    const val NOTE: String =
        "Body Fit is a wellness tool, not a medical device. Every figure it shows is an " +
            "estimate, worked out on this phone from sensor readings and published " +
            "population averages rather than measured clinically. Read the numbers as a " +
            "guide to your own trends over time, not as a reading of your health. Nothing " +
            "here is intended to diagnose, treat, cure or prevent any condition. Speak to " +
            "a qualified clinician about any symptom or health decision that concerns you."

    /** The same claim in one line, for a dialog or the foot of a card. */
    const val SHORT: String =
        "An estimate from population averages, for wellness only. Not a medical measurement."
}

/** The full wellness statement, in the recessive style every screen sets it in. */
@Composable
fun WellnessNote(modifier: Modifier = Modifier, text: String = Wellness.NOTE) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
