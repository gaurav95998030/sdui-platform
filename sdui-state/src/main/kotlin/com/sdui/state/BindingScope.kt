package com.sdui.state

import com.sdui.core.json.EmptyJsonObject
import com.sdui.state.expression.ValueScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Name resolution for bindings, layered most-specific-first:
 *
 * 1. **locals** — `repeat` aliases (`item`, `index`) and action payloads (`response`, `error`).
 * 2. **screen state** — the current screen's store.
 * 3. **global state** — session-wide values (user, wallet, flags).
 * 4. **env** — read-only config (`env.apiBase`, `env.appVersion`, `env.platform`).
 *
 * The explicit namespaces `state`, `global`, `env` and `local` are always available
 * so an author can disambiguate when a screen key shadows a global one.
 */
public data class BindingScope(
    private val screen: JsonObject = EmptyJsonObject,
    private val global: JsonObject = EmptyJsonObject,
    private val env: JsonObject = EmptyJsonObject,
    private val locals: Map<String, JsonElement> = emptyMap(),
) : ValueScope {

    override fun resolve(name: String): JsonElement? = when (name) {
        "state", "screen" -> screen
        "global", "session" -> global
        "env", "config" -> env
        "local" -> JsonObject(locals)
        // JsonNull is deliberately treated as absent: an explicit null in state must
        // behave like a missing key, or `?:` fallbacks and `visible` guards would see
        // a "present" value and the UI would print the literal text "null".
        else -> (locals[name] ?: screen[name] ?: global[name] ?: env[name])
            ?.takeUnless { it is JsonNull }
    }

    /** Derives a child scope with additional locals — used per `repeat` iteration. */
    public fun withLocals(extra: Map<String, JsonElement>): BindingScope =
        if (extra.isEmpty()) this
        else BindingScope(screen, global, env, locals + extra)

    public fun withLocal(name: String, value: JsonElement): BindingScope =
        withLocals(mapOf(name to value))

    public fun withScreen(state: JsonObject): BindingScope =
        BindingScope(state, global, env, locals)

    public companion object {
        public val Empty: BindingScope = BindingScope()
    }
}
