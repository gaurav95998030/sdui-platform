package com.sdui.renderer

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.sdui.actions.ActionContext
import com.sdui.actions.ActionDispatcher
import com.sdui.actions.FormRegistry
import com.sdui.analytics.AnalyticsDispatcher
import com.sdui.core.SduiLogger
import com.sdui.core.model.ModifierSpec
import com.sdui.core.model.SduiNode
import com.sdui.core.model.StyleSpec
import com.sdui.core.registry.Registry
import com.sdui.state.BindingScope
import com.sdui.state.StateStore
import com.sdui.state.expression.ExpressionEngine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A node whose bindings have been evaluated against current state.
 *
 * Renderers receive this rather than the raw [SduiNode] so that Compose can skip
 * a leaf whose *resolved* output did not change, even though some unrelated part
 * of the screen's state did.
 */
@Immutable
public data class ResolvedNode(
    val node: SduiNode,
    val props: JsonObject,
    val modifier: ModifierSpec,
    val style: StyleSpec,
) {
    val type: String get() = node.type
    val id: String? get() = node.id
    val children: List<SduiNode> get() = node.children
    val actions: Map<String, com.sdui.core.model.ActionSpec> get() = node.actions
}

/** The layout container a node sits in; needed for `weight` and `alignment`. */
@Stable
public sealed interface LayoutParent {
    public data object None : LayoutParent
    public class Row(public val scope: RowScope) : LayoutParent
    public class Column(public val scope: ColumnScope) : LayoutParent
    public class Box(public val scope: BoxScope) : LayoutParent
}

/**
 * Per-node render context. [path] is the node's position in the tree (`root.children[2]`)
 * and doubles as its stable Compose key and analytics identifier.
 */
@Immutable
public data class RenderScope(
    val path: String,
    val locals: Map<String, JsonElement> = emptyMap(),
    val parent: LayoutParent = LayoutParent.None,
    val depth: Int = 0,
) {
    public fun child(index: Int, parent: LayoutParent): RenderScope =
        copy(path = "$path.$index", parent = parent, depth = depth + 1)

    public fun withLocals(extra: Map<String, JsonElement>): RenderScope =
        if (extra.isEmpty()) this else copy(locals = locals + extra)
}

/**
 * Everything a screen's subtree shares. Held in a static CompositionLocal because
 * it never changes for the lifetime of a screen — only the [BindingScope] does.
 */
@Stable
public class ScreenRuntime(
    public val entryId: String,
    public val screenId: String,
    public val screenState: StateStore,
    public val expressions: ExpressionEngine,
    public val dispatcher: ActionDispatcher,
    public val forms: FormRegistry,
    public val analytics: AnalyticsDispatcher?,
    public val logger: SduiLogger,
    public val strictMode: Boolean,
    /** Builds a dispatch context; locals carry `repeat` aliases into actions. */
    public val actionContext: (locals: Map<String, JsonElement>) -> ActionContext,
) {
    /** Fires an action bound to a node event, if one is declared. */
    public fun dispatch(
        node: ResolvedNode,
        event: String,
        locals: Map<String, JsonElement> = emptyMap(),
    ) {
        val action = node.actions[event] ?: return
        dispatcher.dispatchAsync(action, actionContext(locals))
    }

    public fun handles(node: ResolvedNode, event: String): Boolean = node.actions.containsKey(event)
}

/** Renders one component type. The single extension point for new UI. */
public interface ComponentRenderer {
    @Composable
    public fun Render(node: ResolvedNode, scope: RenderScope)
}

/**
 * Maps `"type"` strings to renderers.
 *
 * There is no `when (node.type)` anywhere in the engine; resolution is a map
 * lookup, so a host adds `"rating"` or `"map"` with one call and the recursive
 * renderer picks it up with no changes to the SDK.
 */
public class ComponentRegistry(
    fallback: ((String) -> ComponentRenderer?)? = null,
) : Registry<ComponentRenderer>("ComponentRegistry", fallback)

public val LocalScreenRuntime: androidx.compose.runtime.ProvidableCompositionLocal<ScreenRuntime> =
    staticCompositionLocalOf { error("No ScreenRuntime provided; render inside SduiScreenHost.") }

public val LocalComponentRegistry: androidx.compose.runtime.ProvidableCompositionLocal<ComponentRegistry> =
    staticCompositionLocalOf { error("No ComponentRegistry provided; render inside SduiScreenHost.") }

/** Changes on every state write; drives re-evaluation of bindings. */
public val LocalBindingScope: androidx.compose.runtime.ProvidableCompositionLocal<BindingScope> =
    androidx.compose.runtime.compositionLocalOf { BindingScope.Empty }
