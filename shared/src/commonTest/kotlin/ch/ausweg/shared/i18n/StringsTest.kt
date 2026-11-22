package ch.ausweg.shared.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsTest {
    private val en = """{ "hello": "Hello {name}", "only.en": "English only", "count": 3 }"""
    private val fr = """{ "hello": "Bonjour {name}" }"""

    @Test
    fun placeholdersAreFilled() {
        val s = Strings.of("fr", fr, en)
        assertEquals("Bonjour Ada", s("hello", "name" to "Ada"))
    }

    @Test
    fun missingKeysFallBackToEnglishThenToTheKey() {
        val s = Strings.of("fr", fr, en)
        assertEquals("English only", s("only.en"))
        assertEquals("nope.key", s("nope.key"))
        assertTrue(s.has("only.en"))
        assertFalse(s.has("nope.key"))
    }

    @Test
    fun unknownPlaceholdersStay() {
        assertEquals("Hi {who}", fill("Hi {who}", mapOf("x" to 1)))
        assertEquals("Hi {who}", fill("Hi {who}", emptyMap()))
    }

    @Test
    fun parsingSkipsNonStrings() {
        assertEquals(setOf("hello", "only.en"), parseStrings(en).keys)
        assertEquals(setOf("name"), placeholders("Hello {name}"))
    }

    @Test
    fun languagesFileIsAnArray() {
        assertEquals(listOf("en", "fr", "de", "it"), parseLanguages("""["en", "fr", "de", "it"]"""))
    }

    @Test
    fun pickLanguageMatchesByLanguageOnly() {
        val available = listOf("en", "fr", "de", "it")
        assertEquals("fr", pickLanguage(listOf("fr-CH", "de-CH"), available))
        assertEquals("de", pickLanguage(listOf("rm_CH", "de_CH"), available))
        assertEquals("en", pickLanguage(listOf("ja"), available))
        assertEquals("en", pickLanguage(emptyList(), available))
    }
}
