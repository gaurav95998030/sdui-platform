package com.sdui.renderer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sdui.analytics.SduiEvents
import com.sdui.core.json.isTruthy
import com.sdui.core.model.AnimationType
import com.sdui.core.model.EasingSpec
import com.sdui.core.model.ModifierSpec
import com.sdui.core.model.SduiNode
import com.sdui.core.model.StyleSpec
import com.sdui.state.BindingScope
import com.sdui.state.expression.ExpressionEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Guards against a malformed or hostile payload recursing until the stack blows. */
private const val MAX_RENDER_DEPTH = 64

/**
 * Renders any node, recursively.
 *
 * The pipeline per node is:
 * 1. **repeat** — expand a collection binding into N sibling subtrees.
 * 2. **visible** — evaluate the guard; skip entirely when false.
 * 3. **resolve** — substitute `{{bindings}}` into props and colour slots.
 * 4. **animate** — wrap in enter/exit transitions when the node declares them.
 * 5. **dispatch** — look the type up in the registry and hand off.
 *
 * Nothing in this function knows any component type: `text`, `column` and a
 * host's custom `rating` all take the same path.
 */
@Composable
public fun RenderNode(node: SduiNode, scope: RenderScope) {
    if (scope.depth > MAX_RENDER_DEPTH) {
        LocalScreenRuntime.current.logger.e(
            "Renderer",
            "Aborting at ${scope.path}: exceeded max depth $MAX_RENDER_DEPTH",
        )
        return
    }

    val repeat = node.repeat
    if (repeat != null) {
        RenderRepeated(node, repeat, scope)
        return
    }

    RenderSingle(node, scope)
}

@Composable
private fun RenderRepeated(
    node: SduiNode,
    repeat: com.sdui.core.model.RepeatSpec,
    scope: RenderScope,
) {
    val runtime = LocalScreenRuntime.current
    val binding = LocalBindingScope.current.withLocals(scope.locals)

    val items = remember(repeat.items, binding) {
        runtime.expressions.evaluate(runtime.expressions.unwrap(repeat.items), binding) as? JsonArray
    }

    if (items.isNullOrEmpty()) {
        repeat.emptyNode?.let { RenderNode(it, scope.copy(path = "${scope.path}.empty")) }
        return
    }

    // Repeat produces siblings, not a container: whatever layout the parent uses
    // (Column, Row, LazyColumn item) governs how the copies are arranged.
    val limited = repeat.limit?.let { items.take(it) } ?: items
    limited.forEachIndexed { index, item ->
        key(scope.path, index) {
            RenderSingle(
                node = node.copy(repeat = null),
                scope = scope.copy(
                    path = "${scope.path}[$index]",
                    locals = scope.locals + mapOf(
                        repeat.itemAlias to item,
                        repeat.indexAlias to JsonPrimitive(index),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun RenderSingle(node: SduiNode, scope: RenderScope) {
    val runtime = LocalScreenRuntime.current
    val registry = LocalComponentRegistry.current
    val binding = LocalBindingScope.current.withLocals(scope.locals)

    val visible = remember(node.visible, binding) {
        evaluateVisibility(node.visible, runtime.expressions, binding)
    }

    val resolved = remember(node, binding) {
        resolveNode(node, runtime.expressions, binding)
    }

    val renderer = registry.get(node.type)
    if (renderer == null) {
        UnknownComponent(node, runtime)
        return
    }

    val animation = node.animation
    if (animation == null) {
        if (visible) renderer.Render(resolved, scope)
        return
    }

    AnimatedVisibility(
        visible = visible,
        enter = animation.enterTransition(),
        exit = animation.exitTransition(),
    ) {
        renderer.Render(resolved, scope)
    }
}

/**
 * Renders each child in order, threading the parent layout scope through so that
 * `weight` and `alignment` resolve against the real container.
 */
@Composable
public fun RenderChildren(
    children: List<SduiNode>,
    scope: RenderScope,
    parent: LayoutParent,
) {
    children.forEachIndexed { index, child ->
        key(child.id ?: "${scope.path}.$index") {
            RenderNode(child, scope.child(index, parent))
        }
    }
}

@Composable
private fun UnknownComponent(node: SduiNode, runtime: ScreenRuntime) {
    runtime.logger.e("Renderer", "No renderer registered for '${node.type}'")
    runtime.analytics?.track(
        SduiEvents.RENDER_ERROR,
        mapOf(
            "screen_id" to JsonPrimitive(runtime.screenId),
            "component" to JsonPrimitive(node.type),
        ),
    )
    if (runtime.strictMode) {
        // Debug and dashboard preview surface the problem instead of hiding it.
        Column(Modifier) {
            Text(
                text = "Unknown component \"${node.type}\"",
                textAlign = TextAlign.Center,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
    // In production an unknown component renders as nothing, so a screen using a
    // newer component still shows everything else.
}

// ---------------------------------------------------------------------------
// Binding resolution
// ---------------------------------------------------------------------------

internal fun evaluateVisibility(
    visible: JsonPrimitive?,
    expressions: ExpressionEngine,
    binding: BindingScope,
): Boolean {
    if (visible == null) return true
    if (!visible.isString) return visible.isTruthy()
    val text = visible.content
    if (!expressions.isDynamic(text)) return visible.isTruthy()
    return expressions.evaluateBoolean(text, binding, default = true)
}

/**
 * Substitutes bindings into the parts of a node that can carry them.
 *
 * `props` is resolved deeply. Style and modifier are resolved only in their colour
 * slots, which is where dynamic values are actually useful — resolving the whole
 * spec on every state change would cost far more than it buys.
 */
internal fun resolveNode(
    node: SduiNode,
    expressions: ExpressionEngine,
    binding: BindingScope,
): ResolvedNode {
    val props = if (node.props.isEmpty()) {
        node.props
    } else {
        expressions.resolveTree(node.props, binding) as? JsonObject ?: node.props
    }

    fun color(raw: String?): String? =
        if (raw != null && expressions.isDynamic(raw)) {
            (expressions.interpolate(raw, binding) as? JsonPrimitive)?.content
        } else {
            raw
        }

    val modifier = if (needsColorResolution(node.modifier, expressions)) {
        node.modifier.copy(background = color(node.modifier.background))
    } else {
        node.modifier
    }

    val style = if (needsColorResolution(node.style, expressions)) {
        node.style.copy(
            textColor = color(node.style.textColor),
            tint = color(node.style.tint),
            containerColor = color(node.style.containerColor),
            contentColor = color(node.style.contentColor),
        )
    } else {
        node.style
    }

    return ResolvedNode(node = node, props = props, modifier = modifier, style = style)
}

private fun needsColorResolution(spec: ModifierSpec, expressions: ExpressionEngine): Boolean =
    spec.background?.let { expressions.isDynamic(it) } == true

private fun needsColorResolution(spec: StyleSpec, expressions: ExpressionEngine): Boolean =
    listOf(spec.textColor, spec.tint, spec.containerColor, spec.contentColor)
        .any { it != null && expressions.isDynamic(it) }

// ---------------------------------------------------------------------------
// Animations
// ---------------------------------------------------------------------------

private fun com.sdui.core.model.AnimationSpec.easingFn() = when (easing) {
    EasingSpec.LINEAR -> LinearEasing
    EasingSpec.EASE_IN -> FastOutLinearInEasing
    EasingSpec.EASE_OUT -> LinearOutSlowInEasing
    EasingSpec.EASE_IN_OUT, EasingSpec.STANDARD, EasingSpec.BOUNCE -> FastOutSlowInEasing
}

internal fun com.sdui.core.model.AnimationSpec.enterTransition(): EnterTransition {
    val spec = tween<Float>(durationMillis = durationMs, delayMillis = delayMs, easing = easingFn())
    val offsetSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMs, delayMs, easingFn())
    val sizeSpec = tween<androidx.compose.ui.unit.IntSize>(durationMs, delayMs, easingFn())
    return when (enter) {
        AnimationType.NONE -> EnterTransition.None
        AnimationType.FADE, AnimationType.SHIMMER -> fadeIn(spec)
        AnimationType.SLIDE_UP -> slideInVertically(offsetSpec) { it } + fadeIn(spec)
        AnimationType.SLIDE_DOWN -> slideInVertically(offsetSpec) { -it } + fadeIn(spec)
        AnimationType.SLIDE_START -> slideInHorizontally(offsetSpec) { -it } + fadeIn(spec)
        AnimationType.SLIDE_END -> slideInHorizontally(offsetSpec) { it } + fadeIn(spec)
        AnimationType.SCALE -> scaleIn(spec) + fadeIn(spec)
        AnimationType.EXPAND, AnimationType.COLLAPSE -> expandVertically(sizeSpec) + fadeIn(spec)
    }
}

internal fun com.sdui.core.model.AnimationSpec.exitTransition(): ExitTransition {
    val spec = tween<Float>(durationMillis = durationMs, easing = easingFn())
    val offsetSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMs, easing = easingFn())
    val sizeSpec = tween<androidx.compose.ui.unit.IntSize>(durationMs, easing = easingFn())
    return when (exit) {
        AnimationType.NONE -> ExitTransition.None
        AnimationType.FADE, AnimationType.SHIMMER -> fadeOut(spec)
        AnimationType.SLIDE_UP -> slideOutVertically(offsetSpec) { -it } + fadeOut(spec)
        AnimationType.SLIDE_DOWN -> slideOutVertically(offsetSpec) { it } + fadeOut(spec)
        AnimationType.SLIDE_START -> slideOutHorizontally(offsetSpec) { -it } + fadeOut(spec)
        AnimationType.SLIDE_END -> slideOutHorizontally(offsetSpec) { it } + fadeOut(spec)
        AnimationType.SCALE -> scaleOut(spec) + fadeOut(spec)
        AnimationType.EXPAND, AnimationType.COLLAPSE -> shrinkVertically(sizeSpec) + fadeOut(spec)
    }
}

/**
 * Convenience accessors used throughout the component library.
 *
 * `JsonNull` is a `JsonPrimitive` whose `content` is the string "null", so every
 * accessor filters it out explicitly — otherwise a binding that resolved to
 * nothing would render the word "null" in the UI.
 */
public fun JsonObject.str(key: String, default: String? = null): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: default

public fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    this[key]?.takeUnless { it is JsonNull }?.isTruthy() ?: default

public fun JsonObject.num(key: String): Float? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toFloatOrNull()

public fun JsonObject.int(key: String, default: Int): Int =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
        ?.content?.toDoubleOrNull()?.toInt() ?: default

public fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

public fun JsonObject.element(key: String): JsonElement? = this[key]
