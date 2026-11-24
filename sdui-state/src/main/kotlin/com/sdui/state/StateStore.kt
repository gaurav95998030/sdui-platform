package com.sdui.state

import com.sdui.core.json.EmptyJsonObject
import com.sdui.core.json.atPath
import com.sdui.core.json.deepMerge
import com.sdui.core.json.isTruthy
import com.sdui.core.json.withPath
import com.sdui.core.json.withoutPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A single reactive JSON document.
 *
 * The whole SDK models state as one immutable [JsonObject] behind a [StateFlow]
 * rather than typed view-models, because the shape of a screen's state is decided
 * by the server at runtime. Writes are copy-on-write at the mutated path only, and
 * every write publishes a new snapshot, so Compose recomposes exactly the readers
 * that observed a changed path.
 */
public class StateStore(
    public val id: String,
    initial: JsonObject = EmptyJsonObject,
) {
    private val _state = MutableStateFlow(initial)
    public val state: StateFlow<JsonObject> = _state.asStateFlow()

    public val snapshot: JsonObject get() = _state.value

    public fun get(path: String): JsonElement? = _state.value.atPath(path)

    public fun getString(path: String, default: String = ""): String =
        (get(path) as? JsonPrimitive)?.content ?: default

    public fun getBoolean(path: String, default: Boolean = false): Boolean =
        get(path)?.isTruthy() ?: default

    public fun set(path: String, value: JsonElement) {
        _state.update { it.withPath(path, value) }
    }

    public fun set(path: String, value: String): Unit = set(path, JsonPrimitive(value))
    public fun set(path: String, value: Boolean): Unit = set(path, JsonPrimitive(value))
    public fun set(path: String, value: Number): Unit = set(path, JsonPrimitive(value))

    /** Deep-merges [values] into the root; existing untouched branches are preserved. */
    public fun merge(values: JsonObject) {
        if (values.isEmpty()) return
        _state.update { it.deepMerge(values) }
    }

    public fun remove(path: String) {
        _state.update { it.withoutPath(path) }
    }

    public fun toggle(path: String) {
        _state.update { it.withPath(path, JsonPrimitive(!it.atPath(path).isTruthy())) }
    }

    public fun update(transform: (JsonObject) -> JsonObject) {
        _state.update(transform)
    }

    public fun reset(values: JsonObject = EmptyJsonObject) {
        _state.value = values
    }

    /** Emits the value at [path] whenever it actually changes. */
    public fun observe(path: String): Flow<JsonElement?> =
        _state.map { it.atPath(path) }.distinctUntilChanged()

    private inline fun MutableStateFlow<JsonObject>.update(transform: (JsonObject) -> JsonObject) {
        while (true) {
            val current = value
            val next = transform(current)
            if (next == current || compareAndSet(current, next)) return
        }
    }
}
