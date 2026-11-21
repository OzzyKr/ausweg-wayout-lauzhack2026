package ch.ausweg.server.ojp

import ch.ausweg.shared.ojp.TripQuery
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Recorded OJP trip answers in [dir] (`data/fixtures/ojp`), one file per origin / destination / via, used when
 * OJP is unreachable so the stage demo never depends on the network. With [record] on, every successful OJP
 * answer is (over)written there: run the demo routes once with `OJP_RECORD=1` and a key, then commit the files.
 */
class OjpFixtures(
    private val dir: Path?,
    private val record: Boolean = false,
) {
    private val log = LoggerFactory.getLogger(OjpFixtures::class.java)

    fun read(query: TripQuery): String? =
        try {
            dir?.resolve(fileName(query))?.takeIf { it.exists() }?.readText()
        } catch (e: IOException) {
            log.warn("Fixture for {} not readable: {}", fileName(query), e.javaClass.simpleName)
            null
        }

    fun write(
        query: TripQuery,
        xml: String,
    ) {
        if (!record || dir == null) return
        try {
            Files.createDirectories(dir)
            dir.resolve(fileName(query)).writeText(stripSituations(xml))
            log.info("Recorded OJP fixture {}", fileName(query))
        } catch (e: IOException) {
            log.warn("Could not record fixture {}: {}", fileName(query), e.javaClass.simpleName)
        }
    }

    companion object {
        private val SITUATIONS = Regex("<(\\w+:)?Situations>.*?</(\\w+:)?Situations>", RegexOption.DOT_MATCHES_ALL)

        /**
         * Fixtures are served moved onto another date, where today's disruption notices make no sense (they're
         * dropped), and one notice can be megabytes. So they're not recorded.
         */
        fun stripSituations(xml: String): String = xml.replace(SITUATIONS, "")

        /** `trip_8501120_8504100.xml`, `trip_8501120_8504100_via_8504200.xml`; anything unsafe becomes `-`. */
        fun fileName(query: TripQuery): String {
            val parts = listOf("trip", query.from, query.to) + query.via.flatMap { listOf("via", it) }
            return parts.joinToString("_") { it.replace(Regex("[^A-Za-z0-9-]"), "-") } + ".xml"
        }
    }
}
