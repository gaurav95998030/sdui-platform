package com.sdui.state

import com.sdui.core.json.EmptyJsonObject
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the store lifecycle: one long-lived global store plus one store per live
 * navigation entry.
 *
 * Screen stores are keyed by *entry* id rather than screen id so that pushing
 * `detail` twice with different arguments gives each copy its own state, and
 * popping an entry releases it.
 */
public class StateManager(
    globalInitial: JsonObject = EmptyJsonObject,
    /** Read-only environment values exposed to bindings as `env.*`. */
    public val environment: JsonObject = EmptyJsonObject,
) {
    public val global: StateStore = StateStore(id = "global", initial = globalInitial)

    private val screens = ConcurrentHashMap<String, StateStore>()

    public fun storeFor(entryId: String, initial: JsonObject = EmptyJsonObject): StateStore =
        screens.getOrPut(entryId) { StateStore(id = entryId, initial = initial) }

    public fun peek(entryId: String): StateStore? = screens[entryId]

    public fun release(entryId: String) {
        screens.remove(entryId)
    }

    public fun releaseAll() {
        screens.clear()
    }

    /** Builds the resolution scope for a screen; see [BindingScope] for layering. */
    public fun scopeFor(entryId: String): BindingScope = BindingScope(
        screen = screens[entryId]?.snapshot ?: EmptyJsonObject,
        global = global.snapshot,
        env = environment,
    )

    public fun scopeFor(screenState: JsonObject): BindingScope = BindingScope(
        screen = screenState,
        global = global.snapshot,
        env = environment,
    )
}
