package ch.ausweg.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A disruption notice OJP attaches to trip results (SIRI situation), as published, untranslated. */
@Serializable
data class Situation(
    val id: String,
    val summary: String? = null,
    val description: String? = null,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val language: String? = null,
)
