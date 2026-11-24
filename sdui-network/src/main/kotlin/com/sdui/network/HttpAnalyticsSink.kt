package com.sdui.network

import com.sdui.analytics.AnalyticsEvent
import com.sdui.analytics.AnalyticsSink
import com.sdui.core.LogLevel
import com.sdui.core.SduiConfig
import com.sdui.core.json.SduiJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Ships the SDK's automatic instrumentation to the SDUI backend.
 *
 * Events are batched rather than sent individually: screen renders and impressions
 * arrive in bursts, and one request per event would put the analytics pipeline on
 * the critical path of scrolling. A batch flushes when it reaches [batchSize] or
 * when [flushIntervalMs] elapses, whichever comes first.
 *
 * Delivery is best-effort. Losing analytics must never affect the UI, so failures
 * are logged and dropped rather than retried indefinitely into an unbounded buffer.
 */
public class HttpAnalyticsSink(
    private val config: SduiConfig,
    private val client: OkHttpClient = OkHttpClientFactory.create(config),
    private val batchSize: Int = 20,
    private val flushIntervalMs: Long = 5_000,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AnalyticsSink {

    // Bounded, drop-oldest: a backend outage must not grow memory without limit.
    private val queue = Channel<AnalyticsEvent>(capacity = 256, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    private val endpoint =
        "${config.baseUrl.trimEnd('/')}/api/v1/projects/${config.projectId}/analytics"

    init {
        scope.launch {
            val batch = mutableListOf<AnalyticsEvent>()
            while (true) {
                // Take whatever has accumulated, then wait a little for more so that
                // a burst becomes one request instead of twenty.
                select<Unit> {
                    queue.onReceive { batch += it }
                    if (batch.isNotEmpty()) onTimeout(flushIntervalMs) { }
                }
                if (batch.size >= batchSize || (batch.isNotEmpty() && queue.isEmpty)) {
                    val toSend = batch.toList()
                    batch.clear()
                    send(toSend)
                }
            }
        }
    }

    override fun track(event: AnalyticsEvent) {
        queue.trySend(event)
    }

    private fun send(events: List<AnalyticsEvent>) {
        if (events.isEmpty()) return
        val payload = JsonObject(
            mapOf(
                "events" to JsonArray(
                    events.map { event ->
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(event.name),
                                "timestamp" to JsonPrimitive(event.timestamp),
                                "properties" to JsonObject(event.properties),
                            ),
                        )
                    },
                ),
            ),
        )

        val request = Request.Builder()
            .url(endpoint)
            .post(SduiJson.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))
            .apply { config.apiKey?.let { header("X-Api-Key", it) } }
            .build()

        runCatching { client.newCall(request).execute().close() }
            .onFailure {
                config.logger.log(
                    LogLevel.DEBUG, "Analytics",
                    "Dropped ${events.size} event(s): ${it.message}",
                )
            }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
