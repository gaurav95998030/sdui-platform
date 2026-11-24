package com.sdui

import com.sdui.core.SduiResult
import com.sdui.core.UiState
import com.sdui.core.contract.ScreenRequest
import com.sdui.core.json.EmptyJsonObject
import com.sdui.core.model.SduiScreen
import com.sdui.navigation.NavEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Owns one navigation entry's lifecycle: fetch the definition, seed its state,
 * run `onLoad`, and re-render when the dashboard republishes it.
 *
 * One controller per [NavEntry] rather than per screen id, so the same screen
 * pushed twice keeps two independent states — the behaviour users expect from a
 * master/detail stack.
 */
public class ScreenController internal constructor(
    private val engine: SduiEngine,
    public val entry: NavEntry,
    parentScope: CoroutineScope,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)

    private val _state = MutableStateFlow<UiState<SduiScreen>>(UiState.Idle)
    public val state: StateFlow<UiState<SduiScreen>> = _state.asStateFlow()

    public val store: com.sdui.state.StateStore = engine.stateManager.storeFor(entry.entryId)

    private var openedAt: Long = 0
    private var loadJob: Job? = null

    init {
        // Arguments are visible to bindings as `{{args.id}}` before the first fetch
        // completes, so a skeleton can already show what the user tapped.
        if (entry.args.isNotEmpty()) store.set("args", entry.args)
        entry.pendingResult?.let { result ->
            store.set("navResult", result)
            engine.navigator.consumeResult(entry.entryId)
        }
        load(forceRefresh = false)
        observeLiveUpdates()
    }

    public fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.value = UiState.Loading(_state.value.dataOrNull)

            val result = engine.screenSource.load(
                ScreenRequest(
                    screenId = entry.screenId,
                    params = entry.args.mapValues { (_, value) ->
                        (value as? JsonPrimitive)?.content ?: value.toString()
                    },
                    forceRefresh = forceRefresh,
                ),
            )

            when (result) {
                is SduiResult.Success -> apply(result.value.screen, result.fromCache)
                is SduiResult.Failure -> {
                    _state.value = UiState.Error(result.error, _state.value.dataOrNull)
                    engine.logger.e("Screen", "Failed to load '${entry.screenId}': ${result.error.message}")
                    engine.analytics.track(
                        com.sdui.analytics.SduiEvents.SCREEN_LOAD_FAILED,
                        mapOf(
                            "screen_id" to JsonPrimitive(entry.screenId),
                            "code" to JsonPrimitive(result.error.code),
                        ),
                    )
                }
            }
        }
    }

    private fun apply(screen: SduiScreen, fromCache: Boolean) {
        // Seed defaults without clobbering values the user already typed, so a
        // background refresh never wipes a half-filled form.
        val seed = screen.state.filterKeys { !store.snapshot.containsKey(it) }
        if (seed.isNotEmpty()) store.merge(JsonObject(seed))

        _state.value = UiState.Success(screen, fromCache = fromCache)
        openedAt = System.currentTimeMillis()

        engine.analytics.screenOpened(screen.id, screen.version, fromCache)
        screen.theme?.let { themeId -> scope.launch { engine.applyTheme(themeId) } }

        if (screen.onLoad.isNotEmpty()) {
            engine.dispatcher.dispatchAsync(screen.onLoad, actionContext(screen))
        }
    }

    /**
     * Applies a republished definition in place. State is preserved, so a designer
     * tweaking padding does not reset the screen the tester is looking at.
     */
    private fun observeLiveUpdates() {
        val updates = engine.liveUpdates ?: return
        scope.launch {
            updates.filter { it.screenId == entry.screenId }.collect { update ->
                engine.logger.i("LivePreview", "Reloading '${entry.screenId}' v${update.version}")
                val embedded = update.envelope?.screen
                if (embedded != null) apply(embedded, fromCache = false) else load(forceRefresh = true)
            }
        }
    }

    internal fun actionContext(
        screen: SduiScreen?,
        locals: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ) = engine.actionContext(
        entryId = entry.entryId,
        screenId = entry.screenId,
        apis = screen?.apis ?: emptyMap(),
        locals = locals,
        onRefresh = { force -> load(force) },
    )

    public fun refresh(): Unit = load(forceRefresh = true)

    internal fun dispose() {
        if (openedAt > 0) {
            engine.analytics.timeSpent(entry.screenId, System.currentTimeMillis() - openedAt)
        }
        engine.forms.releaseEntry(entry.entryId)
        engine.stateManager.release(entry.entryId)
        scope.cancel()
    }
}

private fun JsonObject.filterKeys(predicate: (String) -> Boolean): Map<String, kotlinx.serialization.json.JsonElement> =
    entries.filter { predicate(it.key) }.associate { it.key to it.value }

internal val EmptyArgs: JsonObject = EmptyJsonObject
