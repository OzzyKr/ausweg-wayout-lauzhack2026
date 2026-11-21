package ch.ausweg.server

import java.nio.file.Path

private const val DEFAULT_PORT = 8080

/** Everything the server reads from the environment. Paths are relative to the repo root. */
data class ServerConfig(
    val port: Int,
    val eventsFile: Path,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val dataDir = Path.of(env["AUSWEG_DATA_DIR"] ?: "data")
            return ServerConfig(
                port = env["PORT"]?.toIntOrNull() ?: DEFAULT_PORT,
                eventsFile = env["AUSWEG_EVENTS"]?.let(Path::of) ?: dataDir.resolve("seed/events.json"),
            )
        }
    }
}
