package app.bodyfit.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Local-date helpers. Every date key in the database is an ISO `yyyy-MM-dd` string. */
object Dates {

    fun today(): String = LocalDate.now().toString()

    /** Local hour of the day, 0 to 23, the key of an [HourlyRecord]. */
    fun currentHour(): Int = LocalTime.now().hour

    /** Local hour an epoch timestamp falls in, used to place a logged drink on the day. */
    fun hourOf(epochMillis: Long): Int =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).hour

    /**
     * Axis label for an hourly chart, for example "6a" or "12p".
     *
     * Deliberately not locale-formatted: the bars are narrow, and a 24-hour locale would
     * still fit "06" where it would not fit "06:00".
     */
    fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12a"
        hour < 12 -> "${hour}a"
        hour == 12 -> "12p"
        else -> "${hour - 12}p"
    }

    /** Range an hourly bar covers, for example "6a - 7a", used when a bar is tapped. */
    fun hourRangeLabel(hour: Int): String = "${hourLabel(hour)} - ${hourLabel((hour + 1) % 24)}"

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate = LocalDate.now()): LocalDate =
        date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The seven ISO date keys of the week containing [date], Monday first. */
    fun weekKeys(date: LocalDate = LocalDate.now()): List<String> {
        val start = weekStart(date)
        return (0L..6L).map { start.plusDays(it).toString() }
    }

    /**
     * Milliseconds until the next local midnight, always at least one.
     *
     * Used to re-anchor the screens on the day boundary. Computed from the calendar rather
     * than a fixed 24 hours so it survives daylight saving and manual clock changes.
     */
    fun millisUntilTomorrow(now: LocalDateTime = LocalDateTime.now()): Long {
        val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay()
        return maxOf(1L, Duration.between(now, tomorrow).toMillis())
    }

    /** The last [n] date keys ending today, oldest first. */
    fun lastNKeys(n: Int, today: LocalDate = LocalDate.now()): List<String> =
        (n - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }

    /**
     * Every ISO date key of the calendar month containing [date], the 1st first.
     *
     * The whole month is returned, including days still to come, so the chart keeps the
     * shape of the month rather than redrawing its width every day.
     */
    fun monthKeys(date: LocalDate = LocalDate.now()): List<String> {
        val start = date.withDayOfMonth(1)
        return (0 until date.lengthOfMonth()).map { start.plusDays(it.toLong()).toString() }
    }

    /** Month and year for a heading, for example "September 2026". */
    fun monthLabel(date: LocalDate = LocalDate.now()): String =
        "${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${date.year}"

    /** Weekday and date for a heading, for example "Mon, 15 Sep". */
    fun dayLabel(key: String): String =
        parse(key).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

    /** Day of the month, used under the trend bars once a week's worth stops fitting. */
    fun dayOfMonthLabel(key: String): String = parse(key).dayOfMonth.toString()

    /** Single-letter weekday label used under the trend bars, for example "M". */
    fun shortWeekdayLabel(key: String): String =
        parse(key).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    /** Three-letter weekday label used in the values table, for example "Mon". */
    fun weekdayLabel(key: String): String =
        parse(key).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}
