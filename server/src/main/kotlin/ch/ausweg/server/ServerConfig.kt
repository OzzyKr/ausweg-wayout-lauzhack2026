package ch.ausweg.server

import ch.ausweg.server.ojp.OjpKey
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private const val DEFAULT_PORT = 8080

/**
 * Everything the server reads from the environment. Paths are relative to the repo root.
 *
 * @property eventsFile old provisional events file, used only while [scenariosDir] has no scenarios.
 * @property ojpKey the first of: env `OJP_API_TOKEN`, env `OJP_API_KEY`, `OJP_API_TOKEN` in `.env`, `ojp.apiKey` in
 *   `local.properties` (both files gitignored). Null means no OJP (fixtures only).
 * @property recordOjp `OJP_RECORD=1`: save every OJP trip answer into [fixturesDir].
 */
data class ServerConfig(
    val port: Int,
    val eventsFile: Path,
    val scenariosDir: Path = Path.of("data/scenarios"),
    val fixturesDir: Path = Path.of("data/fixtures/ojp"),
    val hubsFile: Path = Path.of("data/routing/hubs.json"),
    val ojpKey: OjpKey? = null,
    val recordOjp: Boolean = false,
) {
    companion object {
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            localProperties: Path = Path.of("local.properties"),
            dotEnv: Path = Path.of(".env"),
        ): ServerConfig {
            val dataDir = Path.of(env["AUSWEG_DATA_DIR"] ?: "data")
            val key =
                listOf("OJP_API_TOKEN", "OJP_API_KEY").firstNotNullOfOrNull { env[it]?.takeIf(String::isNotBlank) }
                    ?: readProperty(dotEnv, "OJP_API_TOKEN")
                    ?: readProperty(localProperties, "ojp.apiKey")
            return ServerConfig(
                port = env["PORT"]?.toIntOrNull() ?: DEFAULT_PORT,
                eventsFile = env["AUSWEG_EVENTS"]?.let(Path::of) ?: dataDir.resolve("seed/events.json"),
                scenariosDir = env["AUSWEG_SCENARIOS"]?.let(Path::of) ?: dataDir.resolve("scenarios"),
                fixturesDir = dataDir.resolve("fixtures/ojp"),
                hubsFile = dataDir.resolve("routing/hubs.json"),
                ojpKey = key?.let(::OjpKey),
                recordOjp = env["OJP_RECORD"] == "1",
            )
        }
    }
}

/**
 * [name] from a `.properties` or simple `.env` file (`NAME=value` lines), or null if the file or the value is
 * missing. Surrounding quotes are removed.
 */
private fun readProperty(
    file: Path,
    name: String,
): String? =
    if (Files.exists(file)) {
        loadProperties(file)
            ?.getProperty(name)
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }
    } else {
        null
    }

private fun loadProperties(file: Path): Properties? =
    try {
        Files.newBufferedReader(file).use { Properties().apply { load(it) } }
    } catch (_: IOException) {
        null
    }
