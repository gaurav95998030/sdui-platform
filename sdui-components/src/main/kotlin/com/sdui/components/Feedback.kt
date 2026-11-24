package com.sdui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.bool
import com.sdui.renderer.buildModifier
import com.sdui.renderer.int
import com.sdui.renderer.num
import com.sdui.renderer.str
import com.sdui.renderer.toTextStyle
import com.sdui.theme.LocalSduiTheme

/** `progress` — indeterminate spinner, or a determinate bar when `value` is bound. */
public object ProgressRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val theme = LocalSduiTheme.current
        val color = theme.color(node.style.tint) ?: MaterialTheme.colorScheme.primary
        val modifier = buildModifier(node.modifier, scope.parent)
        val value = node.props.num("value")

        if (node.props.str("variant") == "linear") {
            if (value != null) {
                LinearProgressIndicator({ value.coerceIn(0f, 1f) }, modifier.fillMaxWidth(), color)
            } else {
                LinearProgressIndicator(modifier.fillMaxWidth(), color)
            }
        } else {
            if (value != null) {
                CircularProgressIndicator({ value.coerceIn(0f, 1f) }, modifier, color)
            } else {
                CircularProgressIndicator(modifier, color)
            }
        }
    }
}

/**
 * `skeleton` — a shimmering placeholder block.
 *
 * Screens use it while `loadingKey` is true, which gives a perceived-performance
 * win over a spinner without the server needing to describe the animation.
 */
public object SkeletonRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val alpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeleton-alpha",
        )
        val corner = (node.modifier.cornerRadius?.topStart ?: 8f).dp

        Column(
            modifier = buildModifier(node.modifier.copy(background = null), scope.parent),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(node.props.int("lines", 1)) { index ->
                Box(
                    Modifier
                        .fillMaxWidth(if (index == node.props.int("lines", 1) - 1) 0.6f else 1f)
                        .height((node.props.num("lineHeight") ?: 16f).dp)
                        .clip(RoundedCornerShape(corner))
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

/** `badge` — small count or dot, typically overlaid in a `box`. */
public object BadgeRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val theme = LocalSduiTheme.current
        val label = node.props.str("text")
        Badge(
            modifier = buildModifier(node.modifier, scope.parent),
            containerColor = theme.color(node.style.containerColor) ?: MaterialTheme.colorScheme.error,
            contentColor = theme.color(node.style.contentColor) ?: MaterialTheme.colorScheme.onError,
        ) {
            if (!label.isNullOrEmpty()) Text(label)
        }
    }
}

/** `chip` — compact, tappable label with an optional leading icon. */
public object ChipRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        AssistChip(
            onClick = { runtime.dispatch(node, "onClick", scope.locals) },
            label = { Text(node.props.str("text").orEmpty(), style = node.style.toTextStyle()) },
            enabled = node.modifier.enabled,
            leadingIcon = node.props.str("icon")?.let { name ->
                {
                    MaterialIcons.resolve(name)?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = buildModifier(node.modifier, scope.parent),
        )
    }
}

/**
 * `empty` — the "nothing here" state, so a screen does not have to compose one out
 * of primitives every time.
 */
public object EmptyStateRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        Column(
            modifier = buildModifier(node.modifier, scope.parent).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.props.str("icon")?.let { name ->
                MaterialIcons.resolve(name)?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            node.props.str("title")?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            node.props.str("message")?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
