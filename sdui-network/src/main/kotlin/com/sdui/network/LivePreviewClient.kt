package com.sdui.network

import com.sdui.core.LogLevel
import com.sdui.core.SduiConfig
import com.sdui.core.contract.ScreenUpdate
import com.sdui.core.json.SduiJson
import com.sdui.core.model.ScreenEnvelope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Server-Sent Events channel that makes the dashboard's "Publish" button land on a
 * running device in under a second.
 *
 * SSE over WebSocket on purpose: the traffic is strictly one-way, it survives
 * proxies that block upgrades, and reconnection is a plain loop rather than a
 * protocol state machine.
 *
 * Wire format:
 * ```
 * event: screen.published
 * data: {"screenId":"home","version":"12","screen":{...}}
 * ```
 * The payload may embed the full envelope, in which case the device re-renders
 * without a round trip; otherwise it re-fetches the named screen.
 */
public class LivePreviewClient(
    private val config: SduiConfig,
    private val client: OkHttpClient = OkHttpClientFactory.createStreaming(config),
) {
    public fun updates(): Flow<ScreenUpdate> = flow {
        while (true) {
            try {
                connect().collect { emit(it) }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                config.logger.log(LogLevel.WARN, "LivePreview", "Stream dropped: ${e.message}")
            }
            delay(config.livePreview.reconnectDelayMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun connect(): Flow<ScreenUpdate> = callbackFlow {
        val url = config.livePreview.streamUrl
            ?: "${config.baseUrl.trimEnd('/')}/api/v1/projects/${config.projectId}/preview/stream" +
            "?env=${config.environment.slug}" +
            (config.livePreview.deviceToken?.let { "&device=$it" } ?: "")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .apply { config.apiKey?.let { header("X-Api-Key", it) } }
            .build()

        val call = client.newCall(request)

        // The read loop runs in its own coroutine so that `awaitClose` is registered
        // immediately. Without that, cancelling the flow would leave the socket
        // parked in a blocking read and the server would keep counting the device
        // as connected until its own timeout fired.
        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Preview stream returned HTTP ${response.code}")
                    }
                    val source = response.body?.source()
                        ?: throw IOException("Preview stream had no body")

                    config.logger.log(LogLevel.INFO, "LivePreview", "Connected to $url")

                    var eventName = "message"
                    val data = StringBuilder()
                    while (isActive && !source.exhausted()) {
                        when (val line = source.readUtf8LineStrict()) {
                            "" -> {
                                // A blank line terminates an event.
                                if (data.isNotEmpty()) {
                                    parse(eventName, data.toString())?.let { trySend(it) }
                                    data.clear()
                                }
                                eventName = "message"
                            }

                            else -> when {
                                line.startsWith(":") -> Unit // comment / heartbeat
                                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                            }
                        }
                    }
                }
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }

        awaitClose {
            // Cancelling the call is what actually unblocks the read above.
            runCatching { call.cancel() }
            reader.cancel()
        }
    }

    private fun parse(event: String, data: String): ScreenUpdate? {
        if (event != "screen.published" && event != "screen.updated" && event != "message") return null
        return runCatching {
            val obj = SduiJson.parseToJsonElement(data) as? JsonObject ?: return null
            val screenId = obj["screenId"]?.jsonPrimitive?.content ?: return null
            val version = obj["version"]?.jsonPrimitive?.content
            val envelope = obj["screen"]?.let {
                SduiJson.decodeFromJsonElement(ScreenEnvelope.serializer(), JsonObject(mapOf("screen" to it)))
            }
            ScreenUpdate(screenId, version, envelope)
        }.onFailure {
            config.logger.log(LogLevel.WARN, "LivePreview", "Unparseable event: ${it.message}")
        }.getOrNull()
    }
}
