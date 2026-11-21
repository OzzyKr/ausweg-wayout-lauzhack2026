package ch.ausweg.server

import kotlinx.serialization.json.Json

/** JSON settings for HTTP, SSE and data files. Unknown keys are ignored so the data format can grow. */
val AuswegJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
