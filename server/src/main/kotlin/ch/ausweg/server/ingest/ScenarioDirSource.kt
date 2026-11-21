package ch.ausweg.server.ingest

import ch.ausweg.server.AuswegJson
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.name
import kotlin.io.path.readText

/** Scenario files end with this; the part before it is the scenario id. */
const val SCENARIO_SUFFIX = ".events.json"

/**
 * Every `*.events.json` in [dir] (`data/scenarios`), one replayable scenario each. A file is re-read when its
 * modification time changes, so editing a scenario needs no restart.
 *
 * A broken file never crashes the server: its last good version is kept (or it's skipped) and a warning names the
 * file. Only file names, ids, station names and counts are logged, never event text.
 */
class ScenarioDirSource(
    private val dir: Path,
) : DisruptionSource {
    private val log = LoggerFactory.getLogger(ScenarioDirSource::class.java)
    private val cache = mutableMapOf<Path, Pair<FileTime, DisruptionData>>()
    private var listed: Map<Path, FileTime>? = null
    private var data = DisruptionData.EMPTY

    @Synchronized
    override fun load(): DisruptionData {
        val files = scenarioFiles()
        if (files != listed) {
            listed = files
            cache.keys.retainAll(files.keys)
            files.forEach { (path, modified) -> if (cache[path]?.first != modified) read(path, modified) }
            data =
                files.keys
                    .sorted()
                    .mapNotNull { cache[it]?.second }
                    .fold(DisruptionData.EMPTY, DisruptionData::plus)
        }
        return data
    }

    private fun scenarioFiles(): Map<Path, FileTime> =
        try {
            Files.list(dir).use { paths ->
                paths
                    .filter { it.name.endsWith(SCENARIO_SUFFIX) }
                    .toList()
                    .associateWith { Files.getLastModifiedTime(it) }
            }
        } catch (_: IOException) {
            emptyMap()
        }

    private fun read(
        path: Path,
        modified: FileTime,
    ) {
        try {
            val file = AuswegJson.decodeFromString<ScenarioFile>(path.readText())
            val id = path.name.removeSuffix(SCENARIO_SUFFIX)
            val folded =
                ScenarioFolder(file.incident, id) { name ->
                    log.warn("Scenario {}: station '{}' not in stationIds, ignored", id, name)
                }.fold(file.events)
            cache[path] = modified to folded
            log.info("Loaded scenario {}: {} events", id, file.events.size)
        } catch (e: IllegalArgumentException) {
            // Also covers SerializationException (a subclass) and bad timestamps.
            log.warn("Scenario file {} is invalid, keeping previous version: {}", path.name, e.javaClass.simpleName)
        } catch (e: IOException) {
            log.warn("Scenario file {} not readable: {}", path.name, e.javaClass.simpleName)
        }
    }
}
