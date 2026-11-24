package com.sdui.core.json

import kotlinx.serialization.json.Json

/**
 * The single JSON configuration used everywhere in the platform.
 *
 * The flags are chosen for *forward compatibility*: an old app must keep working
 * when the dashboard starts emitting new keys or new enum values, because the
 * whole point of SDUI is that the server moves faster than the client.
 *
 * - `ignoreUnknownKeys` — new server fields are skipped, not fatal.
 * - `coerceInputValues` — an unrecognised enum/null falls back to the property default.
 * - `isLenient` — tolerates unquoted values from hand-written JSON in the builder.
 * - `explicitNulls = false` — nulls are omitted on encode, keeping payloads small.
 */
public val SduiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = false
    allowStructuredMapKeys = false
    prettyPrint = false
}

/** Same settings, but human-readable — used by the dashboard export and by logs. */
public val SduiJsonPretty: Json = Json(SduiJson) {
    prettyPrint = true
    encodeDefaults = false
}
