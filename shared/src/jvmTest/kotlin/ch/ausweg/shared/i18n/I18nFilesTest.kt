package ch.ausweg.shared.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The key check from the spec, on the real files in `composeApp/.../files/i18n/`:
 * - every language in `languages.json` has a valid file, with exactly en.json's keys, no empty texts, and the same
 *   `{placeholders}` as English;
 * - every key the app uses is in en.json: `t("…")` calls in composeApp, and every key shared code builds
 *   ([I18nKeys]).
 */
class I18nFilesTest {
    private val root = File(System.getProperty("user.dir")).parentFile
    private val dir = File(root, "composeApp/src/commonMain/composeResources/files/i18n")
    private val en = parseStrings(File(dir, "en.json").readText())

    @Test
    fun everyLanguageHasEveryKeyWithTheSamePlaceholders() {
        val languages = parseLanguages(File(dir, "languages.json").readText())
        assertTrue(FALLBACK_LANGUAGE in languages, "languages.json must list en")
        for (lang in languages) {
            val file = File(dir, "$lang.json")
            assertTrue(file.exists(), "missing ${file.name}")
            val texts = parseStrings(file.readText())
            assertEquals(en.keys - texts.keys, emptySet(), "$lang.json misses keys")
            assertEquals(texts.keys - en.keys, emptySet(), "$lang.json has keys en.json doesn't")
            texts.forEach { (key, text) ->
                assertTrue(text.isNotBlank(), "$lang.json: empty $key")
                assertEquals(placeholders(en.getValue(key)), placeholders(text), "$lang.json: placeholders of $key")
            }
        }
    }

    @Test
    fun everyKeySharedCodeBuildsExists() {
        val missing = (I18nKeys.constants + I18nKeys.built).filter { it !in en }
        if (missing.isNotEmpty()) fail("en.json misses keys built in shared: $missing")
    }

    @Test
    fun everyKeyTheScreensUseExists() {
        val call = Regex("""\bt\(\s*"([^"]+)"""")
        val used =
            File(root, "composeApp/src")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { f -> call.findAll(f.readText()).map { it.groupValues[1] to f.name } }
                .toList()
        val missing = used.filter { (key, _) -> key !in en }
        if (missing.isNotEmpty()) fail("en.json misses keys used in composeApp: $missing")
    }
}
