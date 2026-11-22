package ch.ausweg.shared.i18n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** The language every other one falls back to; `en.json` is the source of truth for keys. */
const val FALLBACK_LANGUAGE = "en"

/**
 * UI strings of one [language]. Files are flat JSON objects, `"plan.find_my_way": "Find my way"`, and templates
 * use `{name}` placeholders. A key missing in [texts] comes from [fallback] (English), then the key itself, so a
 * missing translation never crashes or blanks a screen.
 */
class Strings(
    val language: String,
    private val texts: Map<String, String>,
    private val fallback: Map<String, String> = texts,
) {
    /** The text for [key] with `{name}` placeholders replaced by [args]. */
    fun get(
        key: String,
        vararg args: Pair<String, Any?>,
    ): String = fill(texts[key] ?: fallback[key] ?: key, args.toMap())

    operator fun invoke(
        key: String,
        vararg args: Pair<String, Any?>,
    ): String = get(key, *args)

    fun has(key: String): Boolean = key in texts || key in fallback

    companion object {
        /** Before the files are loaded: every lookup shows its key. */
        val EMPTY = Strings(FALLBACK_LANGUAGE, emptyMap())

        /** [language]'s file over the English one, both as JSON text. */
        fun of(
            language: String,
            json: String?,
            englishJson: String,
        ): Strings {
            val english = parseStrings(englishJson)
            val own = json?.let(::parseStrings) ?: english
            return Strings(language, own, english)
        }
    }
}

private val placeholder = Regex("\\{([a-zA-Z0-9_]+)\\}")

/** Replaces `{name}` with `args[name]`; unknown placeholders stay as they are. */
fun fill(
    template: String,
    args: Map<String, Any?>,
): String =
    if (args.isEmpty()) {
        template
    } else {
        placeholder.replace(template) { m ->
            if (m.groupValues[1] in args) args[m.groupValues[1]].toString() else m.value
        }
    }

/** Placeholder names used in [template], for the key-check test. */
fun placeholders(template: String): Set<String> = placeholder.findAll(template).map { it.groupValues[1] }.toSet()

/** A flat `{ "key": "text" }` JSON object. Non-string values are skipped. */
fun parseStrings(json: String): Map<String, String> =
    (Json.parseToJsonElement(json) as? JsonObject)
        .orEmpty()
        .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content } }
        .toMap()

/** `languages.json`: a JSON array of language codes, e.g. `["en", "fr", "de", "it"]`. */
fun parseLanguages(json: String): List<String> =
    (Json.parseToJsonElement(json) as? JsonArray)
        .orEmpty()
        .map { it.jsonPrimitive.content }

/**
 * The first of [preferred] (device locales like `fr-CH`, then a saved choice first if there is one) that is
 * [available], compared by language only; [FALLBACK_LANGUAGE] otherwise.
 */
fun pickLanguage(
    preferred: List<String>,
    available: List<String>,
): String =
    preferred
        .map { it.substringBefore('-').substringBefore('_').lowercase() }
        .firstOrNull { it in available }
        ?: FALLBACK_LANGUAGE
