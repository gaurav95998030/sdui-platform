package com.sdui.core.model

import com.sdui.core.json.EmptyJsonObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One node of a server-described UI tree.
 *
 * The model is deliberately *open*: [type] is a free-form string resolved through
 * the [com.sdui.core.registry.Registry] at render time and [props] is an untyped
 * bag. That is what lets a host app register `"rating"` or `"map"` without any
 * change to this class — the schema never enumerates component kinds.
 *
 * ```json
 * {
 *   "type": "column",
 *   "id": "root",
 *   "visible": "{{user.isPremium}}",
 *   "modifier": { "padding": 16, "width": "match_parent" },
 *   "children": [
 *     { "type": "text", "props": { "text": "Hi {{user.name}}" } }
 *   ]
 * }
 * ```
 */
@Serializable
public data class SduiNode(
    val type: String,
    val id: String? = null,

    /** `true`/`false` literal or a binding expression such as `"{{cart.count > 0}}"`. */
    val visible: JsonPrimitive? = null,

    val modifier: ModifierSpec = ModifierSpec.None,
    val style: StyleSpec = StyleSpec.None,
    val animation: AnimationSpec? = null,

    /** Component-specific properties; values may contain `{{expressions}}`. */
    val props: JsonObject = EmptyJsonObject,

    /** Event name (`onClick`, `onLongClick`, `onChange`, …) to action. */
    val actions: Map<String, ActionSpec> = emptyMap(),

    /** Repeats this node once per item of a bound collection. */
    @SerialName("repeat")
    val repeat: RepeatSpec? = null,

    val analytics: NodeAnalyticsSpec? = null,

    val children: List<SduiNode> = emptyList(),
) {
    /** Stable identity used for Compose `key()` and analytics de-duplication. */
    public fun keyAt(path: String): String = id ?: "$type@$path"
}

/**
 * `dataBinding` for collections.
 *
 * ```json
 * "repeat": { "items": "{{cart.lines}}", "as": "line", "indexAs": "i" }
 * ```
 * Inside the repeated subtree, `{{line.title}}` and `{{i}}` resolve per iteration.
 */
@Serializable
public data class RepeatSpec(
    val items: String,
    @SerialName("as") val itemAlias: String = "item",
    @SerialName("indexAs") val indexAlias: String = "index",
    val limit: Int? = null,
    val emptyNode: SduiNode? = null,
)

@Serializable
public data class NodeAnalyticsSpec(
    /** Fires once when the node first becomes ≥50% visible. */
    val impressionEvent: String? = null,
    val clickEvent: String? = null,
    val properties: JsonObject = EmptyJsonObject,
)
