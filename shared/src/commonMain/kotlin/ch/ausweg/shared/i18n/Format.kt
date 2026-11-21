package ch.ausweg.shared.i18n

import ch.ausweg.shared.model.EndType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** `1 h 19` or `45 min`. */
fun formatDuration(
    d: Duration,
    s: Strings,
): String {
    val total = d.inWholeMinutes.coerceAtLeast(0)
    val h = total / MINUTES_PER_HOUR
    val m = total % MINUTES_PER_HOUR
    return if (h > 0) {
        s(I18nKeys.DURATION_HM, "h" to h, "min" to m.toString().padStart(2, '0'))
    } else {
        s(I18nKeys.DURATION_M, "min" to m)
    }
}

/** `25 Sep 2026` (Zurich date). */
fun formatDate(
    instant: Instant,
    s: Strings,
): String {
    val d = SwissTime.local(instant).date
    return s(I18nKeys.DATE, "day" to d.day, "month" to s(I18nKeys.month(d.month.ordinal + 1)), "year" to d.year)
}

/** `just now`, `11 min ago`, `3 h ago`, or the date when it's a day or more. [now] is the backend clock. */
fun formatAgo(
    then: Instant,
    now: Instant,
    s: Strings,
): String {
    val age = now - then
    return when {
        age < 1.minutes -> s(I18nKeys.JUST_NOW)
        age < 1.hours -> s(I18nKeys.AGO_MIN, "n" to age.inWholeMinutes)
        age < DAY_HOURS.hours -> s(I18nKeys.AGO_H, "n" to age.inWholeHours)
        else -> formatDate(then, s)
    }
}

/** `until approx. 18:00`, `at least until 18:00`, `until 18:00`, or `end unknown` when [until] is null. */
fun formatUntil(
    until: Instant?,
    type: EndType?,
    s: Strings,
): String {
    if (until == null) return s(I18nKeys.END_UNKNOWN)
    val key =
        when (type) {
            EndType.APPROX -> I18nKeys.UNTIL_APPROX
            EndType.AT_LEAST -> I18nKeys.UNTIL_AT_LEAST
            EndType.UNKNOWN, null -> I18nKeys.UNTIL
        }
    return s(key, "time" to SwissTime.hhmm(until))
}

private const val MINUTES_PER_HOUR = 60
private const val DAY_HOURS = 24
