package ch.ausweg.shared.disruption

import ch.ausweg.shared.disruption.Fx.fire
import ch.ausweg.shared.disruption.Fx.t
import ch.ausweg.shared.model.DisruptionHints
import ch.ausweg.shared.model.DisruptionStatus
import ch.ausweg.shared.model.DisruptionText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayTest {
    private val fr = DisruptionText("Incendie", "Trafic interrompu")
    private val de = DisruptionText("Brand", "Unterbruch")
    private val it = DisruptionText("Incendio", "Interruzione")

    @Test
    fun textInTheUiLanguageWhenPublished() {
        assertEquals(de, fire.copy(texts = mapOf("fr" to fr, "de" to de)).text("de"))
    }

    @Test
    fun otherwiseThePublishedLanguagesInOrder() {
        // No English text: French comes first in PUBLISHED_LANGUAGES, then German, then Italian.
        assertEquals(fr, fire.copy(texts = mapOf("it" to it, "de" to de, "fr" to fr)).text("en"))
        assertEquals(de, fire.copy(texts = mapOf("it" to it, "de" to de)).text("en"))
        val rm = DisruptionText("Incendi", "")
        assertEquals(rm, fire.copy(texts = mapOf("rm" to rm)).text("en"))
        assertNull(fire.copy(texts = emptyMap()).text("en"))
    }

    @Test
    fun blockingFollowsTheHints() {
        assertTrue(fire.copy(hints = listOf(DisruptionHints.LINE_CLOSED)).isBlocking)
        assertFalse(fire.copy(hints = listOf(DisruptionHints.NO_ALTERNATIVE)).isBlocking)
    }

    @Test
    fun activeAndBlockingFirstThenNewest() {
        val closed = fire.copy(id = "closed", hints = listOf(DisruptionHints.LINE_CLOSED), publishedAt = t("14:00"))
        val newerClosed = closed.copy(id = "newer", publishedAt = t("14:20"))
        val warning = fire.copy(id = "warning", hints = emptyList(), publishedAt = t("14:30"))
        val ended = closed.copy(id = "ended", status = DisruptionStatus.ENDED, publishedAt = t("14:40"))
        val sorted = sortForDisplay(listOf(ended, warning, closed, newerClosed))
        assertEquals(listOf("newer", "closed", "warning", "ended"), sorted.map { it.id })
    }
}
