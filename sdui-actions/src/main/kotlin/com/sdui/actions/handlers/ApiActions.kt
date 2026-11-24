package com.sdui.actions.handlers

import com.sdui.actions.ActionContext
import com.sdui.actions.ActionHandler
import com.sdui.actions.ResolvedAction
import com.sdui.analytics.SduiEvents
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.json.SduiJson
import com.sdui.core.json.atPath
import com.sdui.core.model.ApiSpec
import com.sdui.state.BindingScope
import com.sdui.state.expression.ExpressionEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `apiCall` — the full request lifecycle a screen expects from one JSON node:
 *
 * ```
 * click -> flip loadingKey -> request (auth, retry, cache)
 *       -> write response into state -> UI recomposes -> run onSuccess chain
 * ```
 *
 * The engine itself never sees a binding: every `{{…}}` in the url, query, headers
 * and body is substituted here against live state first.
 */
public class ApiCallHandler : ActionHandler {

    override suspend fun handle(
        action: ResolvedAction,
        context: ActionContext,
    ): SduiResult<JsonElement?> {
        val engine = context.apiEngine
            ?: return SduiResult.failure(
                SduiError.ActionFailed("apiCall", "No ApiEngine configured for this SDUI instance"),
            )

        val spec = resolveSpec(action, context)
            ?: return SduiResult.failure(
                SduiError.ActionFailed(
                    "apiCall",
                    "Unknown api '${action.string("api")}'. Declare it under screen.apis.",
                ),
            )

        val store = context.screenState
        spec.loadingKey?.let { store.set(it, true) }
        spec.errorKey?.let { store.remove(it) }

        val started = System.currentTimeMillis()
        val result = engine.execute(spec)
        val elapsed = System.currentTimeMillis() - started
        spec.loadingKey?.let { store.set(it, false) }

        return when (result) {
            is SduiResult.Success -> {
                val body = result.value.body ?: JsonNull
                val selected = spec.responsePath?.let { body.atPath(it) } ?: body

                spec.stateKey?.let { key ->
                    val value = if (action.bool("append")) append(store.get(key), selected) else selected
                    store.set(key, value)
                }

                context.analytics?.track(
                    SduiEvents.API_SUCCESS,
                    mapOf(
                        "screen_id" to JsonPrimitive(context.screenId),
                        "url" to JsonPrimitive(spec.url),
                        "status" to JsonPrimitive(result.value.status),
                        "duration_ms" to JsonPrimitive(elapsed),
                        "from_cache" to JsonPrimitive(result.value.fromCache),
                    ),
                )

                if (spec.onSuccess.isNotEmpty()) {
                    context.dispatcher.dispatchAll(
                        spec.onSuccess,
                        context.withLocals(mapOf("response" to selected)),
                    )
                }
                SduiResult.success(selected)
            }

            is SduiResult.Failure -> {
                spec.errorKey?.let { store.set(it, result.error.message) }

                context.analytics?.track(
                    SduiEvents.API_FAILURE,
                    mapOf(
                        "screen_id" to JsonPrimitive(context.screenId),
                        "url" to JsonPrimitive(spec.url),
                        "code" to JsonPrimitive(result.error.code),
                        "duration_ms" to JsonPrimitive(elapsed),
                    ),
                )

                if (spec.onError.isNotEmpty()) {
                    context.dispatcher.dispatchAll(
                        spec.onError,
                        context.withLocals(mapOf("error" to JsonPrimitive(result.error.message))),
                    )
                }
                result
            }
        }
    }

    /** Appends a page of results to whatever the list already holds. */
    private fun append(existing: JsonElement?, incoming: JsonElement?): JsonElement {
        val current = existing as? JsonArray ?: JsonArray(emptyList())
        val next = incoming as? JsonArray ?: return current
        return JsonArray(current + next)
    }

    private fun resolveSpec(action: ResolvedAction, context: ActionContext): ApiSpec? {
        val declared = action.string("api")?.let { context.apis[it] }
        val inline = action.obj("request")?.let {
            runCatching { SduiJson.decodeFromJsonElement(ApiSpec.serializer(), it) }.getOrNull()
        }
        val base = declared ?: inline ?: return null

        // Extra query params supplied at the call site (page number, filters).
        val extraQuery = action.obj("query")?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }
            .orEmpty()

        val overrideBody = action.params["body"]

        return base.resolve(context.expressions, context.bindingScope())
            .let { resolved ->
                resolved.copy(
                    query = resolved.query + extraQuery,
                    body = overrideBody ?: resolved.body,
                )
            }
    }
}

/**
 * Substitutes every binding in a spec against the current scope.
 *
 * Exposed so the screen loader can resolve `onLoad` APIs with the same rules the
 * action path uses.
 */
public fun ApiSpec.resolve(expressions: ExpressionEngine, scope: BindingScope): ApiSpec = copy(
    url = expressions.interpolate(url, scope).asPlainString(url),
    headers = headers.mapValues { (_, value) -> expressions.interpolate(value, scope).asPlainString(value) },
    query = query
        .mapValues { (_, value) -> expressions.interpolate(value, scope).asPlainString(value) }
        .filterValues { it.isNotEmpty() && it != "null" },
    body = body?.let { expressions.resolveTree(it, scope) },
    stateKey = stateKey?.let { expressions.interpolate(it, scope).asPlainString(it) },
)

private fun JsonElement?.asPlainString(fallback: String): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    is JsonObject, is JsonArray -> toString()
    else -> fallback
}
