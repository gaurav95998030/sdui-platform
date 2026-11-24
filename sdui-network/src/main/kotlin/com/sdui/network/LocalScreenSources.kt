package com.sdui.network

import android.content.Context
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.contract.ScreenRequest
import com.sdui.core.contract.ScreenSource
import com.sdui.core.contract.ScreenUpdate
import com.sdui.core.json.SduiJson
import com.sdui.core.model.ScreenEnvelope
import com.sdui.core.model.SduiScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Serves screens bundled in `assets/<directory>/<screenId>.json`.
 *
 * Two production uses: shipping a guaranteed-renderable fallback for the first
 * launch before any network call succeeds, and running the app with no backend at
 * all during development.
 */
public class AssetScreenSource(
    private val context: Context,
    private val directory: String = "sdui",
) : ScreenSource {

    override suspend fun load(request: ScreenRequest): SduiResult<ScreenEnvelope> =
        withContext(Dispatchers.IO) {
            SduiResult.catching {
                val path = "$directory/${request.screenId}.json"
                val text = try {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                } catch (e: java.io.FileNotFoundException) {
                    throw SduiError.ScreenNotFound(request.screenId)
                }
                val element = SduiJson.parseToJsonElement(text) as? JsonObject
                    ?: throw SduiError.Parse("Asset $path is not a JSON object")
                if (element.containsKey("screen")) {
                    SduiJson.decodeFromJsonElement(ScreenEnvelope.serializer(), element)
                } else {
                    ScreenEnvelope(SduiJson.decodeFromJsonElement(SduiScreen.serializer(), element))
                }
            }
        }
}

/** Serves screens held in memory — the backing store for tests and previews. */
public class InMemoryScreenSource(
    private val screens: MutableMap<String, ScreenEnvelope> = mutableMapOf(),
) : ScreenSource {

    public fun put(screen: SduiScreen) {
        screens[screen.id] = ScreenEnvelope(screen)
    }

    override suspend fun load(request: ScreenRequest): SduiResult<ScreenEnvelope> =
        screens[request.screenId]
            ?.let { SduiResult.success(it) }
            ?: SduiResult.failure(SduiError.ScreenNotFound(request.screenId))
}

/**
 * Tries each source in order and returns the first success.
 *
 * The canonical arrangement is `Composite(remote, assets)`: live content wins,
 * bundled JSON covers a cold start with no connectivity.
 */
public class CompositeScreenSource(
    private val sources: List<ScreenSource>,
) : ScreenSource {

    override suspend fun load(request: ScreenRequest): SduiResult<ScreenEnvelope> {
        var lastError: SduiError? = null
        for (source in sources) {
            when (val result = source.load(request)) {
                is SduiResult.Success -> return result
                is SduiResult.Failure -> lastError = result.error
            }
        }
        return SduiResult.failure(lastError ?: SduiError.ScreenNotFound(request.screenId))
    }

    override fun updates(): Flow<ScreenUpdate> = sources.map { it.updates() }.merge()
}
