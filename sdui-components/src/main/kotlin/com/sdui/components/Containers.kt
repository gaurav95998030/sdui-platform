package com.sdui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.sdui.core.model.AlignmentSpec
import com.sdui.core.model.ArrangementSpec
import com.sdui.core.model.CrossAxisAlignment
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LayoutParent
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderChildren
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.buildModifier
import com.sdui.renderer.num
import com.sdui.renderer.str
import com.sdui.renderer.toAlignment
import com.sdui.renderer.toShape
import com.sdui.theme.LocalSduiTheme

private fun parseArrangement(raw: String?): ArrangementSpec? = raw?.let { value ->
    when (value) {
        "start", "top" -> ArrangementSpec.START
        "center" -> ArrangementSpec.CENTER
        "end", "bottom" -> ArrangementSpec.END
        "spaceBetween" -> ArrangementSpec.SPACE_BETWEEN
        "spaceAround" -> ArrangementSpec.SPACE_AROUND
        "spaceEvenly" -> ArrangementSpec.SPACE_EVENLY
        else -> null
    }
}

private fun parseCrossAxis(raw: String?): CrossAxisAlignment? = raw?.let { value ->
    when (value) {
        "start", "top" -> CrossAxisAlignment.START
        "center" -> CrossAxisAlignment.CENTER
        "end", "bottom" -> CrossAxisAlignment.END
        "stretch" -> CrossAxisAlignment.STRETCH
        "baseline" -> CrossAxisAlignment.BASELINE
        else -> null
    }
}

/**
 * `column` — vertical stack.
 *
 * `spacing` is applied as `Arrangement.spacedBy`, which composes correctly with
 * `justify` (`spaceBetween` etc.) unlike inserting spacer children.
 */
public object ColumnRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val spacing = node.props.num("spacing") ?: 0f
        val justify = parseArrangement(node.props.str("justify") ?: node.props.str("verticalArrangement"))
        val align = parseCrossAxis(node.props.str("align") ?: node.props.str("horizontalAlignment"))

        val vertical: Arrangement.Vertical = when (justify) {
            ArrangementSpec.CENTER -> Arrangement.spacedBy(spacing.dp, Alignment.CenterVertically)
            ArrangementSpec.END -> Arrangement.spacedBy(spacing.dp, Alignment.Bottom)
            ArrangementSpec.SPACE_BETWEEN -> Arrangement.SpaceBetween
            ArrangementSpec.SPACE_AROUND -> Arrangement.SpaceAround
            ArrangementSpec.SPACE_EVENLY -> Arrangement.SpaceEvenly
            ArrangementSpec.START, null -> Arrangement.spacedBy(spacing.dp, Alignment.Top)
        }

        Column(
            modifier = buildModifier(
                spec = node.modifier,
                parent = scope.parent,
                onClick = if (runtime.handles(node, "onClick")) {
                    { runtime.dispatch(node, "onClick", scope.locals) }
                } else {
                    null
                },
            ),
            verticalArrangement = vertical,
            horizontalAlignment = when (align) {
                CrossAxisAlignment.CENTER -> Alignment.CenterHorizontally
                CrossAxisAlignment.END -> Alignment.End
                else -> Alignment.Start
            },
        ) {
            RenderChildren(node.children, scope, LayoutParent.Column(this))
        }
    }
}

/** `row` — horizontal stack, mirroring [ColumnRenderer]. */
public object RowRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val spacing = node.props.num("spacing") ?: 0f
        val justify = parseArrangement(node.props.str("justify") ?: node.props.str("horizontalArrangement"))
        val align = parseCrossAxis(node.props.str("align") ?: node.props.str("verticalAlignment"))

        val horizontal: Arrangement.Horizontal = when (justify) {
            ArrangementSpec.CENTER -> Arrangement.spacedBy(spacing.dp, Alignment.CenterHorizontally)
            ArrangementSpec.END -> Arrangement.spacedBy(spacing.dp, Alignment.End)
            ArrangementSpec.SPACE_BETWEEN -> Arrangement.SpaceBetween
            ArrangementSpec.SPACE_AROUND -> Arrangement.SpaceAround
            ArrangementSpec.SPACE_EVENLY -> Arrangement.SpaceEvenly
            ArrangementSpec.START, null -> Arrangement.spacedBy(spacing.dp, Alignment.Start)
        }

        Row(
            modifier = buildModifier(
                spec = node.modifier,
                parent = scope.parent,
                onClick = if (runtime.handles(node, "onClick")) {
                    { runtime.dispatch(node, "onClick", scope.locals) }
                } else {
                    null
                },
            ),
            horizontalArrangement = horizontal,
            verticalAlignment = when (align) {
                CrossAxisAlignment.CENTER -> Alignment.CenterVertically
                CrossAxisAlignment.END -> Alignment.Bottom
                else -> Alignment.Top
            },
        ) {
            RenderChildren(node.children, scope, LayoutParent.Row(this))
        }
    }
}

/** `box` — overlays children; the only container where child `alignment` is free-form. */
public object BoxRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val contentAlignment = node.props.str("align")
            ?.let { raw -> AlignmentSpec.entries.firstOrNull { it.name.replace("_", "").equals(raw, true) } }
            ?.toAlignment() ?: Alignment.TopStart

        Box(
            modifier = buildModifier(
                spec = node.modifier,
                parent = scope.parent,
                onClick = if (runtime.handles(node, "onClick")) {
                    { runtime.dispatch(node, "onClick", scope.locals) }
                } else {
                    null
                },
            ),
            contentAlignment = contentAlignment,
        ) {
            RenderChildren(node.children, scope, LayoutParent.Box(this))
        }
    }
}

/**
 * `card` — an elevated surface that lays its children out as a column.
 *
 * The Material container handles the shape, tonal elevation and click ripple, so
 * `modifier.cornerRadius`/`elevation` are consumed here rather than by the generic
 * modifier pipeline (which would double-apply the shadow).
 */
public object CardRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val theme = LocalSduiTheme.current
        val elevation = node.modifier.elevation ?: 1f
        val shape = (node.modifier.cornerRadius ?: com.sdui.core.model.Corners.all(12f)).toShape()
        val container = theme.color(node.style.containerColor ?: node.modifier.background)
            ?: MaterialTheme.colorScheme.surfaceContainerLow
        val spacing = node.props.num("spacing") ?: 0f

        // The card owns background/elevation/shape; strip them so buildModifier
        // does not paint a second surface underneath.
        val innerSpec = node.modifier.copy(
            background = null, backgroundGradient = null, elevation = null, cornerRadius = null,
        )
        val onClick: (() -> Unit)? = if (runtime.handles(node, "onClick")) {
            { runtime.dispatch(node, "onClick", scope.locals) }
        } else {
            null
        }

        val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
                RenderChildren(node.children, scope, LayoutParent.Column(this))
            }
        }

        if (onClick != null) {
            Card(
                onClick = onClick,
                enabled = node.modifier.enabled,
                modifier = buildModifier(innerSpec, scope.parent),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = container),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
                content = content,
            )
        } else {
            Card(
                modifier = buildModifier(innerSpec, scope.parent),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = container),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
                content = content,
            )
        }
    }
}

/** `surface` — a card without elevation semantics; useful for section backgrounds. */
public object SurfaceRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val theme = LocalSduiTheme.current
        val shape = node.modifier.cornerRadius.toShape()
        Surface(
            modifier = buildModifier(node.modifier.copy(background = null, cornerRadius = null), scope.parent),
            shape = shape,
            color = theme.color(node.modifier.background) ?: MaterialTheme.colorScheme.surface,
            tonalElevation = (node.modifier.elevation ?: 0f).dp,
        ) {
            Column {
                RenderChildren(node.children, scope, LayoutParent.Column(this))
            }
        }
    }
}
