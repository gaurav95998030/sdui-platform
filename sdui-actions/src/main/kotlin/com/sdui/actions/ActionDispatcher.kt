package com.sdui.actions

import com.sdui.analytics.SduiEvents
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.json.EmptyJsonObject
import com.sdui.core.json.isTruthy
import com.sdui.core.model.ActionSpec
import com.sdui.core.registry.Registry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/** An action whose `{{bindings}}` have already been substituted. */
public data class ResolvedAction(
    val type: String,
    val params: JsonObject,
    val spec: ActionSpec,
) {
    // JsonNull is filtered everywhere: it is a JsonPrimitive whose content is the
    // string "null", which would otherwise reach handlers as a real value.
    public fun string(key: String, default: String? = null): String? =
        (params[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: default

    public fun bool(key: String, default: Boolean = false): Boolean =
        params[key]?.takeUnless { it is JsonNull }?.isTruthy() ?: default

    public fun int(key: String, default: Int): Int =
        (params[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
            ?.content?.toDoubleOrNull()?.toInt() ?: default

    public fun long(key: String, default: Long): Long =
        (params[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
            ?.content?.toDoubleOrNull()?.toLong() ?: default

    public fun obj(key: String): JsonObject? = params[key] as? JsonObject
}

/**
 * A pluggable side effect.
 *
 * @return a value that becomes `{{result}}` for chained actions — an API response,
 * a scanned barcode, a dialog choice.
 */
public fun interface ActionHandler {
    public suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?>
}

public class ActionRegistry : Registry<ActionHandler>("ActionRegistry")

/**
 * Runs server-described actions.
 *
 * The dispatcher owns everything that is *common* to all actions — binding
 * substitution, `if`/`else` guards, debounce, `then`/`onError` chaining and
 * analytics — so a handler only implements its own single effect. That split is
 * what keeps adding an action to a few lines of code.
 */
public class ActionDispatcher(
    public val registry: ActionRegistry,
) {
    private val lastRunAt = ConcurrentHashMap<String, Long>()

    /** Fire-and-forget entry point used by UI callbacks. */
    public fun dispatchAsync(action: ActionSpec, context: ActionContext) {
        context.coroutineScope.launch { dispatch(action, context) }
    }

    public fun dispatchAsync(actions: List<ActionSpec>, context: ActionContext) {
        if (actions.isEmpty()) return
        context.coroutineScope.launch { dispatchAll(actions, context) }
    }

    public suspend fun dispatchAll(
        actions: List<ActionSpec>,
        context: ActionContext,
    ): SduiResult<JsonElement?> {
        var last: SduiResult<JsonElement?> = SduiResult.success(null)
        for (action in actions) {
            last = dispatch(action, context)
            if (last is SduiResult.Failure && !action.ignoreFailure) return last
        }
        return last
    }

    public suspend fun dispatch(
        action: ActionSpec,
        context: ActionContext,
    ): SduiResult<JsonElement?> {
        // 1. Guard.
        action.condition?.let { condition ->
            val passes = context.expressions.evaluateBoolean(condition, context.bindingScope())
            if (!passes) return dispatchAll(action.otherwise, context)
        }

        // 2. Debounce, keyed by screen + type + params so two buttons don't share a window.
        if (action.debounceMs > 0) {
            val key = "${context.entryId}:${action.type}:${action.params.hashCode()}"
            val now = System.currentTimeMillis()
            val previous = lastRunAt[key]
            if (previous != null && now - previous < action.debounceMs) {
                context.logger.d("Actions", "Debounced ${action.type}")
                return SduiResult.success(null)
            }
            lastRunAt[key] = now
        }

        // 3. Resolve bindings inside params.
        val resolvedParams = context.expressions
            .resolveTree(action.params, context.bindingScope()) as? JsonObject
            ?: EmptyJsonObject
        val resolved = ResolvedAction(action.type, resolvedParams, action)

        val handler = registry.get(action.type)
        if (handler == null) {
            val error = SduiError.UnknownAction(action.type)
            context.logger.e("Actions", error.message)
            context.analytics?.track(
                SduiEvents.ACTION_FAILED,
                mapOf(
                    "action" to JsonPrimitive(action.type),
                    "reason" to JsonPrimitive("unregistered"),
                    "screen_id" to JsonPrimitive(context.screenId),
                ),
            )
            return dispatchAll(action.onError, context).let { SduiResult.failure(error) }
        }

        // 4. Run.
        val result = try {
            handler.handle(resolved, context)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            SduiResult.failure(SduiError.ActionFailed(action.type, t.message ?: "Action threw", t))
        }

        context.analytics?.track(
            SduiEvents.ACTION_DISPATCHED,
            mapOf(
                "action" to JsonPrimitive(action.type),
                "screen_id" to JsonPrimitive(context.screenId),
                "success" to JsonPrimitive(result.isSuccess),
            ),
        )

        // 5. Chain. `then` sees the produced value as {{result}}.
        return when (result) {
            is SduiResult.Success -> {
                val next = context.withLocals(mapOf("result" to (result.value ?: JsonNull)))
                if (action.then.isEmpty()) result else dispatchAll(action.then, next)
            }

            is SduiResult.Failure -> {
                context.logger.w("Actions", "${action.type} failed: ${result.error.message}")
                val next = context.withLocals(
                    mapOf(
                        "error" to JsonPrimitive(result.error.message),
                        "errorCode" to JsonPrimitive(result.error.code),
                    ),
                )
                if (action.onError.isNotEmpty()) dispatchAll(action.onError, next)
                else if (action.otherwise.isNotEmpty()) dispatchAll(action.otherwise, next)
                if (action.ignoreFailure) SduiResult.success(null) else result
            }
        }
    }
}
