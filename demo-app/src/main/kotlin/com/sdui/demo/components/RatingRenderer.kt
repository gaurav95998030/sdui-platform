package com.sdui.demo.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.buildModifier
import com.sdui.renderer.int
import com.sdui.renderer.num
import com.sdui.renderer.str
import com.sdui.theme.LocalSduiTheme
import kotlinx.serialization.json.JsonPrimitive

/**
 * A host-defined component, registered with one line in `DemoApplication`:
 *
 * ```kotlin
 * Sdui.registerComponent("rating", RatingRenderer)
 * ```
 *
 * From that point `{"type": "rating", "props": {"value": 4}}` renders anywhere,
 * including in screens published after the app shipped. No SDK change, no
 * `when` branch, no reflection — this is the whole extension story.
 */
object RatingRenderer : ComponentRenderer {

    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val theme = LocalSduiTheme.current
        val value = node.props.num("value")?.toInt() ?: 0
        val max = node.props.int("max", 5)
        val size = (node.props.num("size") ?: 20f).dp
        val editable = runtime.handles(node, "onChange")
        val tint = theme.color(node.style.tint ?: "brandGold")
            ?: MaterialTheme.colorScheme.primary

        Row(modifier = buildModifier(node.modifier, scope.parent)) {
            for (star in 1..max) {
                Icon(
                    imageVector = if (star <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = node.props.str("label") ?: "$value out of $max",
                    tint = tint,
                    modifier = Modifier
                        .size(size)
                        .let { base ->
                            if (!editable) {
                                base
                            } else {
                                base.clickable {
                                    runtime.dispatch(
                                        node,
                                        "onChange",
                                        scope.locals + mapOf("value" to JsonPrimitive(star)),
                                    )
                                }
                            }
                        },
                )
            }
        }
    }
}
