package ch.ausweg.server.ingest

import ch.ausweg.server.AuswegJson
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * Reads [path] and re-reads it whenever its modification time changes, so whatever rewrites the file
 * (the data pipeline, or a live feed later) is picked up without a restart.
 *
 * A missing or broken file never crashes the server: it keeps the last good data (or none) and logs a warning.
 * Only ids and counts are logged, never disruption text.
 */
class JsonFileSource(
    private val path: Path,
) : DisruptionSource {
    private val log = LoggerFactory.getLogger(JsonFileSource::class.java)
    private var loadedAt: FileTime? = null
    private var data = DisruptionData.EMPTY

    @Synchronized
    override fun load(): DisruptionData {
        val modified =
            try {
                Files.getLastModifiedTime(path)
            } catch (e: IOException) {
                if (loadedAt !=
                    MISSING
                ) {
                    log.warn("Events file {} not readable ({}), serving no data", path, e.javaClass.simpleName)
                }
                loadedAt = MISSING
                return data
            }
        if (modified != loadedAt) {
            loadedAt = modified
            try {
                data = AuswegJson.decodeFromString<EventsFile>(Files.readString(path)).toData()
                log.info("Loaded {} updates, {} scenarios from {}", data.updates.size, data.scenarios.size, path)
            } catch (e: IllegalArgumentException) {
                // Also covers SerializationException (a subclass) and bad timestamps.
                log.warn("Events file {} is invalid, keeping previous data: {}", path, e.javaClass.simpleName)
            }
        }
        return data
    }

    private companion object {
        val MISSING: FileTime = FileTime.fromMillis(Long.MIN_VALUE)
    }
}
