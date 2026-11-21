package ch.ausweg.shared.i18n

import ch.ausweg.shared.disruption.UpdateKind
import ch.ausweg.shared.model.AdviceReason
import ch.ausweg.shared.model.CommunityMatch
import ch.ausweg.shared.model.InfoSource
import ch.ausweg.shared.state.LoadError
import ch.ausweg.shared.state.Tab

private const val MONTHS = 12

/**
 * i18n keys that shared code builds. The composables use their own keys through `t("…")`; the key-check test
 * (`shared/src/jvmTest`) checks both sets against `en.json`, and calls every function here for every enum value.
 */
object I18nKeys {
    const val DURATION_HM = "time.duration_hm"
    const val DURATION_M = "time.duration_m"
    const val DATE = "time.date"
    const val JUST_NOW = "time.just_now"
    const val AGO_MIN = "time.ago_min"
    const val AGO_H = "time.ago_h"
    const val UNTIL_APPROX = "time.until_approx"
    const val UNTIL_AT_LEAST = "time.until_at_least"
    const val UNTIL = "time.until"
    const val END_UNKNOWN = "time.end_unknown"

    /** Every constant above, for the key-check test. */
    val constants: List<String> =
        listOf(
            DURATION_HM,
            DURATION_M,
            DATE,
            JUST_NOW,
            AGO_MIN,
            AGO_H,
            UNTIL_APPROX,
            UNTIL_AT_LEAST,
            UNTIL,
            END_UNKNOWN,
        )

    fun month(number: Int): String = "time.month.$number"

    /** Why we picked what we picked (options screen, «our pick» card). */
    fun adviceReason(reason: AdviceReason): String = "advice.${reason.name.lowercase()}"

    /** «From a similar disruption on 12 Sep» and friends. */
    fun communityMatch(match: CommunityMatch): String = "community.match.${match.name.lowercase()}"

    /** Timeline tag: Public / VIA / SBB internal. */
    fun infoSource(source: InfoSource): String = "timeline.source.${source.name.lowercase()}"

    /** The update banner's text. */
    fun update(kind: UpdateKind): String = "update.${kind.name.lowercase()}"

    /** Why the options screen is empty. */
    fun loadError(error: LoadError): String = "options.error.${error.name.lowercase()}"

    /** Bottom bar label. */
    fun tab(tab: Tab): String = "tab.${tab.name.lowercase()}"

    /** Every key the functions above can build, for the key-check test. */
    val built: List<String>
        get() =
            (1..MONTHS).map(::month) +
                AdviceReason.entries.map(::adviceReason) +
                CommunityMatch.entries.map(::communityMatch) +
                InfoSource.entries.map(::infoSource) +
                UpdateKind.entries.map(::update) +
                LoadError.entries.map(::loadError) +
                Tab.entries.map(::tab)
}
