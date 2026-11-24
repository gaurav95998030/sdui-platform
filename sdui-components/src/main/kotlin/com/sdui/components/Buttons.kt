package com.sdui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.bool
import com.sdui.renderer.buildModifier
import com.sdui.renderer.str
import com.sdui.renderer.toShape
import com.sdui.renderer.toTextStyle
import com.sdui.theme.LocalSduiTheme

/**
 * `button` — text, optional leading/trailing icon and a built-in loading state.
 *
 * ```json
 * { "type": "button",
 *   "props": { "text": "Pay {{currency(cart.total)}}", "variant": "filled",
 *              "loading": "{{ui.paying}}", "enabled": "{{cart.items.length > 0}}",
 *              "leadingIcon": "creditCard" },
 *   "modifier": { "width": "match_parent" },
 *   "actions": { "onClick": { "type": "apiCall", "params": { "api": "pay" } } } }
 * ```
 *
 * `loading` binds straight to the `loadingKey` an `apiCall` toggles, so a button
 * shows a spinner for the duration of its own request with no extra wiring.
 */
public object ButtonRenderer : ComponentRenderer {

    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val theme = LocalSduiTheme.current

        val label = node.props.str("text") ?: node.props.str("title") ?: ""
        val loading = node.props.bool("loading")
        // `enabled` is accepted on props (bindable) as well as on modifier (static).
        val enabled = node.modifier.enabled && node.props.bool("enabled", true) &&
            !loading && !node.props.bool("disabled")
        val variant = node.props.str("variant") ?: node.style.variant ?: "filled"

        val onClick: () -> Unit = { runtime.dispatch(node, "onClick", scope.locals) }
        val modifier = buildModifier(
            spec = node.modifier.copy(background = null, cornerRadius = null, elevation = null),
            parent = scope.parent,
        )

        val colors = when (variant) {
            "outlined" -> ButtonDefaults.outlinedButtonColors(
                contentColor = theme.color(node.style.contentColor) ?: MaterialTheme.colorScheme.primary,
            )

            "text" -> ButtonDefaults.textButtonColors(
                contentColor = theme.color(node.style.contentColor) ?: MaterialTheme.colorScheme.primary,
            )

            "tonal" -> ButtonDefaults.filledTonalButtonColors(
                containerColor = theme.color(node.style.containerColor ?: node.modifier.background)
                    ?: MaterialTheme.colorScheme.secondaryContainer,
                contentColor = theme.color(node.style.contentColor)
                    ?: MaterialTheme.colorScheme.onSecondaryContainer,
            )

            else -> ButtonDefaults.buttonColors(
                containerColor = theme.color(node.style.containerColor ?: node.modifier.background)
                    ?: MaterialTheme.colorScheme.primary,
                contentColor = theme.color(node.style.contentColor) ?: MaterialTheme.colorScheme.onPrimary,
            )
        }

        val shape = node.modifier.cornerRadius?.toShape() ?: ButtonDefaults.shape

        val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            ButtonContent(node, label, loading)
        }

        when (variant) {
            "outlined" -> OutlinedButton(onClick, modifier, enabled, shape, colors, content = content)
            "text" -> TextButton(onClick, modifier, enabled, shape, colors, content = content)
            "tonal" -> FilledTonalButton(onClick, modifier, enabled, shape, colors, content = content)
            "elevated" -> ElevatedButton(onClick, modifier, enabled, shape, colors, content = content)
            else -> Button(onClick, modifier, enabled, shape, colors, content = content)
        }
    }

    @Composable
    private fun ButtonContent(node: ResolvedNode, label: String, loading: Boolean) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = androidx.compose.material3.LocalContentColor.current,
            )
            return
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.props.str("leadingIcon")?.let { name ->
                MaterialIcons.resolve(name)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (label.isNotEmpty()) {
                Text(text = label, style = node.style.toTextStyle(MaterialTheme.typography.labelLarge))
            }
            node.props.str("trailingIcon")?.let { name ->
                MaterialIcons.resolve(name)?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** `iconButton` — a tappable glyph with the standard 48dp touch target. */
public object IconButtonRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val theme = LocalSduiTheme.current
        val name = node.props.str("icon") ?: node.props.str("name") ?: return
        val vector = MaterialIcons.resolve(name) ?: return

        IconButton(
            onClick = { runtime.dispatch(node, "onClick", scope.locals) },
            enabled = node.modifier.enabled,
            modifier = buildModifier(node.modifier, scope.parent),
        ) {
            Icon(
                imageVector = vector,
                contentDescription = node.modifier.contentDescription ?: name,
                tint = theme.color(node.style.tint) ?: Color.Unspecified,
            )
        }
    }
}
