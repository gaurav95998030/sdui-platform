package com.sdui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.sdui.core.model.ContentScaleSpec
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.buildModifier
import com.sdui.renderer.int
import com.sdui.renderer.num
import com.sdui.renderer.str
import com.sdui.renderer.toCompose
import com.sdui.renderer.toTextStyle
import com.sdui.theme.LocalSduiTheme

/**
 * `text` — the most used component in any SDUI system.
 *
 * ```json
 * { "type": "text",
 *   "props": { "text": "Hi {{user.firstName}}, {{cart.items.length}} items" },
 *   "style": { "typography": "titleMedium", "textColor": "onSurface", "maxLines": 2 } }
 * ```
 */
public object TextRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val text = node.props.str("text") ?: node.props.str("value") ?: ""
        val clickable = runtime.handles(node, "onClick")

        Text(
            text = text,
            style = node.style.toTextStyle(),
            maxLines = node.style.maxLines ?: Int.MAX_VALUE,
            overflow = node.style.overflow.toCompose(),
            modifier = buildModifier(
                spec = node.modifier,
                parent = scope.parent,
                onClick = if (clickable) {
                    { runtime.dispatch(node, "onClick", scope.locals) }
                } else {
                    null
                },
            ),
        )
    }
}

/**
 * `image` — remote or bundled artwork, loaded through Coil so the SDK never
 * blocks composition on I/O.
 */
public object ImageRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val url = node.props.str("url") ?: node.props.str("src") ?: node.props.str("source")
        val clickable = runtime.handles(node, "onClick")
        val contentScale = node.props.str("contentScale")
            ?.let { raw -> ContentScaleSpec.entries.firstOrNull { it.name.equals(raw, true) || it.serialName() == raw } }
            ?.toCompose() ?: ContentScale.Crop

        val modifier = buildModifier(
            spec = node.modifier,
            parent = scope.parent,
            onClick = if (clickable) {
                { runtime.dispatch(node, "onClick", scope.locals) }
            } else {
                null
            },
        )

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(node.props.int("crossfadeMs", 200))
                .build(),
            contentDescription = node.modifier.contentDescription ?: node.props.str("alt"),
            contentScale = contentScale,
            modifier = modifier,
        )
    }

    private fun ContentScaleSpec.serialName(): String = when (this) {
        ContentScaleSpec.FIT -> "fit"
        ContentScaleSpec.CROP -> "crop"
        ContentScaleSpec.FILL_BOUNDS -> "fillBounds"
        ContentScaleSpec.FILL_WIDTH -> "fillWidth"
        ContentScaleSpec.FILL_HEIGHT -> "fillHeight"
        ContentScaleSpec.INSIDE -> "inside"
        ContentScaleSpec.NONE -> "none"
    }

    private fun ContentScaleSpec.toCompose(): ContentScale = when (this) {
        ContentScaleSpec.FIT -> ContentScale.Fit
        ContentScaleSpec.CROP -> ContentScale.Crop
        ContentScaleSpec.FILL_BOUNDS -> ContentScale.FillBounds
        ContentScaleSpec.FILL_WIDTH -> ContentScale.FillWidth
        ContentScaleSpec.FILL_HEIGHT -> ContentScale.FillHeight
        ContentScaleSpec.INSIDE -> ContentScale.Inside
        ContentScaleSpec.NONE -> ContentScale.None
    }
}

/**
 * `spacer` — fixed gap. `size` fills the cross axis of whichever container it is
 * in, so one definition works in both a row and a column.
 */
public object SpacerRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val size = node.props.num("size")
        val width = node.props.num("width") ?: size
        val height = node.props.num("height") ?: size

        var modifier: Modifier = buildModifier(node.modifier, scope.parent)
        if (width != null) modifier = modifier.width(width.dp)
        if (height != null) modifier = modifier.height(height.dp)
        if (width == null && height == null && node.modifier.weight == null) {
            modifier = modifier.size(8.dp)
        }
        Spacer(modifier)
    }
}

/** `divider` — a hairline rule; `orientation: "vertical"` for row separators. */
public object DividerRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val theme = LocalSduiTheme.current
        val color = theme.color(node.props.str("color") ?: node.style.tint)
            ?: MaterialTheme.colorScheme.outlineVariant
        val thickness = (node.props.num("thickness") ?: 1f).dp
        val modifier = buildModifier(node.modifier, scope.parent)

        if (node.props.str("orientation") == "vertical") {
            VerticalDivider(modifier = modifier, thickness = thickness, color = color)
        } else {
            HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
        }
    }
}

/**
 * `icon` — Material symbols by name. Names resolve through [MaterialIcons] rather
 * than reflection, so R8 can still strip unused icons.
 */
public object IconRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val theme = LocalSduiTheme.current
        val name = node.props.str("name") ?: node.props.str("icon") ?: return
        val vector = MaterialIcons.resolve(name) ?: return
        val clickable = runtime.handles(node, "onClick")

        Icon(
            imageVector = vector,
            contentDescription = node.modifier.contentDescription ?: name,
            tint = theme.color(node.style.tint ?: node.style.textColor)
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = buildModifier(
                spec = node.modifier,
                parent = scope.parent,
                onClick = if (clickable) {
                    { runtime.dispatch(node, "onClick", scope.locals) }
                } else {
                    null
                },
            ).let { base ->
                node.props.num("size")?.let { base.size(it.dp) } ?: base
            },
        )
    }
}
