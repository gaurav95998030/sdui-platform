package com.sdui.actions.handlers

import com.sdui.actions.ActionContext
import com.sdui.actions.ActionHandler
import com.sdui.actions.ResolvedAction
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.json.EmptyJsonObject
import com.sdui.core.model.NavigationMode
import com.sdui.core.model.SduiScreen
import com.sdui.navigation.NavigationRequest
import com.sdui.state.StateStore
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun ActionContext.store(scope: String?): StateStore =
    if (scope == "global" || scope == "session") globalState else screenState

/**
 * `setState` — the primitive every interactive screen is built from.
 *
 * ```json
 * { "type": "setState", "params": { "path": "filters.month", "value": "2026-08" } }
 * { "type": "setState", "params": { "values": { "ui.expanded": true, "ui.tab": 2 } } }
 * ```
 */
public object SetStateHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val store = context.store(action.string("scope"))

        action.obj("values")?.let { values ->
            values.forEach { (path, value) -> store.set(path, value) }
            return SduiResult.success(values)
        }

        val path = action.string("path") ?: action.string("key")
            ?: return SduiResult.failure(SduiError.ActionFailed("setState", "Missing 'path'"))
        val value = action.params["value"] ?: JsonNull
        store.set(path, value)
        return SduiResult.success(value)
    }
}

/** `toggleState` — flips a boolean at a path; the workhorse for expand/collapse. */
public object ToggleStateHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val path = action.string("path") ?: action.string("key")
            ?: return SduiResult.failure(SduiError.ActionFailed("toggleState", "Missing 'path'"))
        val store = context.store(action.string("scope"))
        store.toggle(path)
        return SduiResult.success(store.get(path))
    }
}

/** `clearState` — removes one path, or resets the whole scope when none is given. */
public object ClearStateHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val store = context.store(action.string("scope"))
        when (val path = action.string("path")) {
            null -> store.reset()
            else -> store.remove(path)
        }
        return SduiResult.success(null)
    }
}

/**
 * `navigate` — the entire navigation graph, resolved at runtime.
 *
 * ```json
 * { "type": "navigate", "params": { "screen": "expenseDetail", "mode": "push",
 *                                   "args": { "id": "{{item.id}}" } } }
 * ```
 */
public object NavigateHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val mode = action.string("mode")?.let { raw ->
            NavigationMode.entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || serialNameOf(it).equals(raw, ignoreCase = true)
            }
        } ?: NavigationMode.PUSH

        val request = NavigationRequest(
            screenId = action.string("screen") ?: action.string("screenId"),
            mode = mode,
            args = action.obj("args") ?: action.obj("params") ?: EmptyJsonObject,
            target = action.string("target"),
            result = action.obj("result"),
            clearStack = action.bool("clearStack"),
        )

        val moved = context.navigator.navigate(request)
        return if (moved) {
            SduiResult.success(JsonPrimitive(request.screenId ?: mode.name))
        } else if (mode == NavigationMode.POP || mode == NavigationMode.POP_TO_ROOT) {
            // Nothing left to pop is a normal outcome, not a failure.
            SduiResult.success(null)
        } else {
            SduiResult.failure(
                SduiError.ActionFailed("navigate", "Could not navigate to '${request.screenId}'"),
            )
        }
    }

    private fun serialNameOf(mode: NavigationMode): String = when (mode) {
        NavigationMode.PUSH -> "push"
        NavigationMode.REPLACE -> "replace"
        NavigationMode.POP -> "pop"
        NavigationMode.POP_TO_ROOT -> "popToRoot"
        NavigationMode.POP_TO -> "popTo"
        NavigationMode.MODAL -> "modal"
        NavigationMode.BOTTOM_SHEET -> "bottomSheet"
    }
}

/** `back` — pops one entry, dismissing an overlay first if one is showing. */
public object BackHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val result = action.obj("result")
        if (!context.navigator.dismissOverlay(result)) context.navigator.pop(result)
        return SduiResult.success(null)
    }
}

/** `refreshScreen` — re-fetches the current definition, bypassing the cache. */
public object RefreshScreenHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        context.refreshScreen(action.bool("force", true))
        return SduiResult.success(null)
    }
}

/** `setTheme` — swaps the design system without an app update. */
public object SetThemeHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val themeId = action.string("theme") ?: action.string("id")
            ?: return SduiResult.failure(SduiError.ActionFailed("setTheme", "Missing 'theme'"))
        context.applyTheme(themeId)
        return SduiResult.success(JsonPrimitive(themeId))
    }
}

/** `sequence` — runs nested actions in order; `then` covers most cases. */
public object SequenceHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val actions = action.params["actions"] ?: return SduiResult.success(null)
        val parsed = runCatching {
            com.sdui.core.json.SduiJson.decodeFromJsonElement(
                kotlinx.serialization.builtins.ListSerializer(com.sdui.core.model.ActionSpec.serializer()),
                actions,
            )
        }.getOrDefault(emptyList())
        return context.dispatcher.dispatchAll(parsed, context)
    }
}

/** `delay` — inserts a pause into a chain (e.g. show a toast, then navigate). */
public object DelayHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        delay(action.long("ms", 300).coerceIn(0, 10_000))
        return SduiResult.success(null)
    }
}

/** `noop` — a placeholder so the dashboard can wire an event before its behaviour exists. */
public object NoopHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> =
        SduiResult.success(null)
}

internal fun JsonObject?.orEmpty(): JsonObject = this ?: EmptyJsonObject

internal val ScreenSchemaVersion: Int = SduiScreen.SCHEMA_VERSION
