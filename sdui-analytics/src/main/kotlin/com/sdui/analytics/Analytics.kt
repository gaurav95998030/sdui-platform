package com.sdui.analytics

import com.sdui.core.SduiLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList

public data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, JsonElement> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    public fun with(vararg pairs: Pair<String, String?>): AnalyticsEvent = copy(
        properties = properties + pairs.mapNotNull { (k, v) ->
            v?.let { k to JsonPrimitive(it) as JsonElement }
        },
    )
}

/** Where events go. Hosts add Firebase, Amplitude, their own pipeline, or all three. */
public fun interface AnalyticsSink {
    public fun track(event: AnalyticsEvent)
}

/** Canonical event names emitted automatically by the SDK. */
public object SduiEvents {
    public const val SCREEN_OPENED: String = "sdui_screen_opened"
    public const val SCREEN_LOAD_FAILED: String = "sdui_screen_load_failed"
    public const val SCREEN_TIME_SPENT: String = "sdui_screen_time_spent"
    public const val COMPONENT_IMPRESSION: String = "sdui_component_impression"
    public const val COMPONENT_CLICKED: String = "sdui_component_clicked"
    public const val ACTION_DISPATCHED: String = "sdui_action_dispatched"
    public const val ACTION_FAILED: String = "sdui_action_failed"
    public const val API_SUCCESS: String = "sdui_api_success"
    public const val API_FAILURE: String = "sdui_api_failure"
    public const val NAVIGATION: String = "sdui_navigation"
    public const val EXPERIMENT_EXPOSED: String = "sdui_experiment_exposed"
    public const val RENDER_ERROR: String = "sdui_render_error"
}

/**
 * Fans events out to every registered sink off the main thread.
 *
 * Instrumentation is *automatic*: the renderer, navigator and API engine emit
 * through this dispatcher, so a screen authored in the dashboard is measured
 * without anyone adding tracking code. Nodes can additionally declare
 * `analytics.impressionEvent` / `analytics.clickEvent` for custom names.
 */
public class AnalyticsDispatcher(
    private val enabled: Boolean = true,
    private val logger: SduiLogger = SduiLogger.Noop,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val sinks = CopyOnWriteArrayList<AnalyticsSink>()

    /** Properties merged into every event (user id, environment, app version). */
    @Volatile
    private var superProperties: Map<String, JsonElement> = emptyMap()

    public fun addSink(sink: AnalyticsSink) {
        sinks.addIfAbsent(sink)
    }

    public fun removeSink(sink: AnalyticsSink) {
        sinks.remove(sink)
    }

    public fun setSuperProperties(properties: Map<String, JsonElement>) {
        superProperties = properties
    }

    public fun track(event: AnalyticsEvent) {
        if (!enabled || sinks.isEmpty()) return
        val enriched = event.copy(properties = superProperties + event.properties)
        scope.launch {
            sinks.forEach { sink ->
                runCatching { sink.track(enriched) }
                    .onFailure { logger.w("Analytics", "Sink threw for ${event.name}", it) }
            }
        }
    }

    public fun track(name: String, properties: Map<String, JsonElement> = emptyMap()) {
        track(AnalyticsEvent(name, properties))
    }

    public fun track(name: String, properties: JsonObject) {
        track(AnalyticsEvent(name, properties.toMap()))
    }

    public fun screenOpened(screenId: String, version: String?, fromCache: Boolean) {
        track(
            SduiEvents.SCREEN_OPENED,
            mapOf(
                "screen_id" to JsonPrimitive(screenId),
                "version" to JsonPrimitive(version ?: "unknown"),
                "from_cache" to JsonPrimitive(fromCache),
            ),
        )
    }

    public fun timeSpent(screenId: String, millis: Long) {
        track(
            SduiEvents.SCREEN_TIME_SPENT,
            mapOf(
                "screen_id" to JsonPrimitive(screenId),
                "duration_ms" to JsonPrimitive(millis),
            ),
        )
    }

    /** Logs to the SDK logger; useful in debug builds and in the preview device. */
    public companion object {
        public fun loggingSink(logger: SduiLogger): AnalyticsSink = AnalyticsSink { event ->
            logger.d("Analytics", "${event.name} ${event.properties}")
        }
    }
}
