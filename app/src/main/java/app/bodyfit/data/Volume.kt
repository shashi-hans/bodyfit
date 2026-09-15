package app.bodyfit.data

import java.util.Locale

/**
 * Prints a water amount at a scale a person would say out loud.
 *
 * Below a litre it stays in millilitres, because "250 ml" is how a glass is described.
 * At a litre and above it switches to litres and drops trailing zeros, so a day's intake
 * reads "3 L" or "1.25 L" rather than "3,000 ml".
 *
 * Amounts are stored in millilitres everywhere; this only changes how they are shown.
 */
object Volume {

    private const val ML_PER_LITRE = 1000.0

    /** "750 ml" or "1.25 L". */
    fun format(ml: Double): String = "${amount(ml)} ${unit(ml)}"

    fun format(ml: Int): String = format(ml.toDouble())

    /** The number on its own, already scaled to whatever [unit] returns for the same input. */
    fun amount(ml: Double): String {
        if (ml < ML_PER_LITRE) return String.format(Locale.getDefault(), "%,d", ml.toInt())
        val litres = ml / ML_PER_LITRE
        return when {
            ml % ML_PER_LITRE == 0.0 -> String.format(Locale.getDefault(), "%,d", litres.toInt())
            ml % 100.0 == 0.0 -> String.format(Locale.getDefault(), "%.1f", litres)
            else -> String.format(Locale.getDefault(), "%.2f", litres)
        }
    }

    fun unit(ml: Double): String = if (ml >= ML_PER_LITRE) "L" else "ml"
}
