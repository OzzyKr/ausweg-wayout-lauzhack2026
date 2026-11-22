package ch.ausweg.shared.state

import kotlin.random.Random

/**
 * Small key-value storage the platform provides (SharedPreferences on Android, localStorage on the web).
 * Implementations live in the composeApp entry points.
 */
interface Settings {
    operator fun get(key: String): String?

    operator fun set(
        key: String,
        value: String,
    )
}

/** In-memory [Settings] for tests and as a fallback when storage is blocked (private browsing). */
class MemorySettings(
    initial: Map<String, String> = emptyMap(),
) : Settings {
    private val values = initial.toMutableMap()

    override fun get(key: String): String? = values[key]

    override fun set(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

object SettingKeys {
    const val VOTER_ID = "voterId"
    const val LANGUAGE = "language"

    /** This device's votes, target id → value (JSON), to highlight and take them back after a reload. */
    const val MY_VOTES = "myVotes"

    /** The picked [ThemeMode] name; missing means follow the device. */
    const val THEME = "theme"
}

private const val VOTER_ID_LENGTH = 16
private const val HEX = "0123456789abcdef"

/** The anonymous voter id: made up once, then kept. No names, no accounts. */
fun Settings.voterId(random: Random = Random): String =
    get(SettingKeys.VOTER_ID) ?: (1..VOTER_ID_LENGTH)
        .map { HEX[random.nextInt(HEX.length)] }
        .joinToString("")
        .also { set(SettingKeys.VOTER_ID, it) }
