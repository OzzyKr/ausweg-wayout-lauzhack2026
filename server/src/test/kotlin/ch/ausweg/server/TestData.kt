package ch.ausweg.server

import ch.ausweg.server.ingest.DisruptionUpdate
import ch.ausweg.shared.model.Disruption
import ch.ausweg.shared.model.DisruptionStatus
import java.nio.file.Path
import kotlin.time.Instant

fun instant(iso: String): Instant = Instant.parse(iso)

fun resourcePath(name: String): Path = Path.of(checkNotNull(FakeWall::class.java.getResource("/$name")).toURI())

fun update(
    id: String,
    version: Int,
    publishedAt: String,
    status: DisruptionStatus = DisruptionStatus.ACTIVE,
    deleted: Boolean = false,
) = DisruptionUpdate(Disruption(id, version, instant(publishedAt), status), deleted)
