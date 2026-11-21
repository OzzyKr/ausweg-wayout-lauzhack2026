package ch.ausweg.shared.model

import kotlinx.serialization.Serializable

/** A stop found by name search (plan screen autocomplete). */
@Serializable
data class Location(
    val stop: StopRef,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
