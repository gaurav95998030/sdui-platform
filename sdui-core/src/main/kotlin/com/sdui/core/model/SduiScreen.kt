package com.sdui.core.model

import com.sdui.core.json.EmptyJsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A complete, self-contained screen definition. This is the unit the backend
 * versions, publishes and rolls back.
 */
@Serializable
public data class SduiScreen(
    val id: String,

    /** Bumped by the SDK when the *wire format* changes; guards forward compat. */
    val schemaVersion: Int = SCHEMA_VERSION,

    /** Content version assigned by the dashboard on publish. */
    val version: String = "1",

    val title: String? = null,
    val root: SduiNode,

    /** Seed values for this screen's state scope. */
    val state: JsonObject = EmptyJsonObject,

    /** Named network calls this screen may invoke. */
    val apis: Map<String, ApiSpec> = emptyMap(),

    val onLoad: List<ActionSpec> = emptyList(),
    val onAppear: List<ActionSpec> = emptyList(),
    val onBack: List<ActionSpec> = emptyList(),

    /** Theme id to activate for this screen; falls back to the global theme. */
    val theme: String? = null,
    val background: String? = null,
    val scrollable: Boolean = false,
    val appBar: AppBarSpec? = null,

    /** Free-form; carries A/B variant ids, author, publish timestamp, etc. */
    val metadata: JsonObject = EmptyJsonObject,
) {
    public companion object {
        public const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
public data class AppBarSpec(
    val title: String? = null,
    val subtitle: String? = null,
    val showBack: Boolean = true,
    val background: String? = null,
    val contentColor: String? = null,
    val elevation: Float = 0f,
    val centered: Boolean = false,
    val navigationAction: ActionSpec? = null,
    val actions: List<SduiNode> = emptyList(),
)

/** Envelope returned by the backend; carries delivery metadata around the screen. */
@Serializable
public data class ScreenEnvelope(
    val screen: SduiScreen,
    val etag: String? = null,
    val ttlSeconds: Long = 0,
    val experiment: ExperimentAssignment? = null,
    val flags: Map<String, Boolean> = emptyMap(),
)

@Serializable
public data class ExperimentAssignment(
    val experimentId: String,
    val variant: String,
)
