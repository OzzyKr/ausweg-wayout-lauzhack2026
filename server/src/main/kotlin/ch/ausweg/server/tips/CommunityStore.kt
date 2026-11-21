package ch.ausweg.server.tips

import ch.ausweg.server.AuswegJson
import ch.ausweg.shared.model.CommunityRoute
import ch.ausweg.shared.model.Tip
import ch.ausweg.shared.model.VoteValue
import ch.ausweg.shared.model.Votes
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.io.path.readText

private val SCHEMA =
    listOf(
        "CREATE TABLE IF NOT EXISTS tips (id TEXT PRIMARY KEY, json TEXT NOT NULL, staff INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS routes (id TEXT PRIMARY KEY, json TEXT NOT NULL, staff INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS votes (target_id TEXT NOT NULL, voter_id TEXT NOT NULL, value TEXT NOT NULL, " +
            "PRIMARY KEY (target_id, voter_id))",
    )

private val TABLES = listOf("tips", "routes")

/**
 * Tips, community routes and votes in SQLite (plain JDBC, one connection, synchronized: the demo has few writes).
 *
 * Tips and routes are stored as JSON with their id and staff flag beside them; votes have their own table with one
 * row per (target, voter), so a voter changing their mind replaces their vote. Vote counts are added when reading.
 */
class CommunityStore private constructor(
    private val db: Connection,
) {
    init {
        SCHEMA.forEach { db.update(it) }
    }

    @Synchronized
    fun tips(): List<Tip> {
        val votes = db.voteCounts()
        return db.rows("tips").map { (id, json, staff) ->
            AuswegJson.decodeFromString<Tip>(json).copy(staffEndorsed = staff, votes = votes[id] ?: Votes())
        }
    }

    @Synchronized
    fun routes(): List<CommunityRoute> {
        val votes = db.voteCounts()
        return db.rows("routes").map { (id, json, staff) ->
            AuswegJson.decodeFromString<CommunityRoute>(json).copy(staffEndorsed = staff, votes = votes[id] ?: Votes())
        }
    }

    /** Stores [tip]. Its votes and staff flag are stored too (used for seed data; new tips have none). */
    @Synchronized
    fun addTip(tip: Tip) {
        insert(
            "tips",
            tip.id,
            AuswegJson.encodeToString(tip.copy(staffEndorsed = false, votes = Votes())),
            tip.staffEndorsed,
        )
        seedVotes(tip.id, tip.votes)
    }

    @Synchronized
    fun addRoute(route: CommunityRoute) {
        val json = AuswegJson.encodeToString(route.copy(staffEndorsed = false, votes = Votes()))
        insert("routes", route.id, json, route.staffEndorsed)
        seedVotes(route.id, route.votes)
    }

    /** Records [voterId]'s vote on a tip or route, replacing an earlier one. False if [targetId] doesn't exist. */
    @Synchronized
    fun vote(
        targetId: String,
        voterId: String,
        value: VoteValue,
    ): Boolean {
        val exists = TABLES.any { db.query("SELECT id FROM $it WHERE id = ?", targetId) { true }.isNotEmpty() }
        if (exists) {
            db.update(
                "INSERT INTO votes (target_id, voter_id, value) VALUES (?, ?, ?) " +
                    "ON CONFLICT (target_id, voter_id) DO UPDATE SET value = excluded.value",
                targetId,
                voterId,
                value.name,
            )
        }
        return exists
    }

    /** Marks a tip or route as endorsed by SBB staff. False if [targetId] doesn't exist. */
    @Synchronized
    fun endorse(targetId: String): Boolean =
        TABLES.sumOf { db.update("UPDATE $it SET staff = 1 WHERE id = ?", targetId) } > 0

    @Synchronized
    fun isEmpty(): Boolean = TABLES.all { db.query("SELECT id FROM $it LIMIT 1") { true }.isEmpty() }

    /** Loads [file] (`data/seed/community.json`) if the store is empty, so the demo never starts with an empty app. */
    @Synchronized
    fun seedIfEmpty(file: Path) {
        if (!isEmpty() || !Files.exists(file)) return
        try {
            val seed = AuswegJson.decodeFromString<SeedFile>(file.readText())
            seed.tips.forEach(::addTip)
            seed.routes.forEach(::addRoute)
            log.info("Seeded {} tips and {} routes from {}", seed.tips.size, seed.routes.size, file)
        } catch (e: IOException) {
            log.warn("Seed file {} not readable: {}", file, e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            log.warn("Seed file {} invalid: {}", file, e.javaClass.simpleName)
        }
    }

    private fun insert(
        table: String,
        id: String,
        json: String,
        staff: Boolean,
    ) = db.update("INSERT INTO $table (id, json, staff) VALUES (?, ?, ?)", id, json, if (staff) 1 else 0)

    /** Seed data carries vote counts: stored as votes from made-up `seed:` voters so they behave like real ones. */
    private fun seedVotes(
        targetId: String,
        votes: Votes,
    ) {
        val all =
            List(votes.up) { VoteValue.UP } + List(votes.down) { VoteValue.DOWN } +
                List(votes.stillValid) { VoteValue.STILL_VALID } + List(votes.outdated) { VoteValue.OUTDATED }
        all.forEachIndexed { i, v -> vote(targetId, "seed:$i", v) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CommunityStore::class.java)

        /** A throwaway store (tests, or when the database file can't be opened). */
        fun inMemory(): CommunityStore = CommunityStore(DriverManager.getConnection("jdbc:sqlite::memory:"))

        /**
         * The store in [file], created if needed. If it can't be opened (read-only disk…), falls back to memory with
         * a warning: the demo keeps working, votes just don't survive a restart.
         */
        fun open(file: Path): CommunityStore =
            try {
                file.toAbsolutePath().parent?.let(Files::createDirectories)
                CommunityStore(DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}"))
            } catch (e: SQLException) {
                log.warn("Database {} unavailable ({}), using memory", file, e.javaClass.simpleName)
                inMemory()
            } catch (e: IOException) {
                log.warn("Database {} unavailable ({}), using memory", file, e.javaClass.simpleName)
                inMemory()
            }
    }
}

private fun Connection.rows(table: String): List<Triple<String, String, Boolean>> =
    query("SELECT id, json, staff FROM $table ORDER BY rowid") {
        Triple(it.getString("id"), it.getString("json"), it.getInt("staff") == 1)
    }

private fun Connection.voteCounts(): Map<String, Votes> =
    this
        .query("SELECT target_id, value, COUNT(*) AS n FROM votes GROUP BY target_id, value") {
            Triple(it.getString("target_id"), VoteValue.valueOf(it.getString("value")), it.getInt("n"))
        }.groupBy({ it.first }, { it.second to it.third })
        .mapValues { (_, counts) ->
            val n = counts.toMap()
            Votes(
                up = n[VoteValue.UP] ?: 0,
                down = n[VoteValue.DOWN] ?: 0,
                stillValid = n[VoteValue.STILL_VALID] ?: 0,
                outdated = n[VoteValue.OUTDATED] ?: 0,
            )
        }

/** Runs an INSERT / UPDATE / DDL statement with positional [args]; returns the changed row count. */
private fun Connection.update(
    sql: String,
    vararg args: Any,
): Int =
    prepareStatement(sql).use { st ->
        args.forEachIndexed { i, arg -> st.setObject(i + 1, arg) }
        st.executeUpdate()
    }

/** Runs a SELECT with positional [args], mapping each row (read columns by name). */
private fun <T> Connection.query(
    sql: String,
    vararg args: Any,
    row: (ResultSet) -> T,
): List<T> =
    prepareStatement(sql).use { st ->
        args.forEachIndexed { i, arg -> st.setObject(i + 1, arg) }
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
    }

/** `data/seed/community.json`. */
@Serializable
private data class SeedFile(
    val tips: List<Tip> = emptyList(),
    val routes: List<CommunityRoute> = emptyList(),
)
