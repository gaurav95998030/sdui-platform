package com.sdui.renderer

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.sdui.core.model.StyleSpec
import com.sdui.core.model.TextAlignSpec
import com.sdui.core.model.TextDecorationSpec
import com.sdui.core.model.TextOverflowSpec
import com.sdui.theme.LocalSduiTheme
import com.sdui.theme.toCompose
import com.sdui.theme.typographyRole

/**
 * Builds a [TextStyle] from a server [StyleSpec].
 *
 * A `typography` role is used as the base so screens inherit the theme's type
 * ramp, and individual fields then override it — that way a theme change moves
 * every label without touching a single screen definition.
 */
@Composable
public fun StyleSpec.toTextStyle(base: TextStyle = LocalTextStyle.current): TextStyle {
    val theme = LocalSduiTheme.current
    val roleStyle = typographyRole(typography) ?: base
    return roleStyle.copy(
        color = theme.color(textColor) ?: roleStyle.color,
        fontSize = fontSize?.sp ?: roleStyle.fontSize,
        fontWeight = fontWeight?.toCompose() ?: roleStyle.fontWeight,
        fontStyle = if (italic) FontStyle.Italic else roleStyle.fontStyle,
        letterSpacing = letterSpacing?.sp ?: roleStyle.letterSpacing,
        lineHeight = lineHeight?.sp ?: roleStyle.lineHeight,
        textAlign = textAlign?.toCompose() ?: roleStyle.textAlign,
        textDecoration = decoration?.toCompose() ?: roleStyle.textDecoration,
    )
}

public fun TextAlignSpec.toCompose(): TextAlign = when (this) {
    TextAlignSpec.START -> TextAlign.Start
    TextAlignSpec.CENTER -> TextAlign.Center
    TextAlignSpec.END -> TextAlign.End
    TextAlignSpec.JUSTIFY -> TextAlign.Justify
}

public fun TextDecorationSpec.toCompose(): TextDecoration = when (this) {
    TextDecorationSpec.NONE -> TextDecoration.None
    TextDecorationSpec.UNDERLINE -> TextDecoration.Underline
    TextDecorationSpec.LINE_THROUGH -> TextDecoration.LineThrough
}

public fun TextOverflowSpec?.toCompose(): TextOverflow = when (this) {
    TextOverflowSpec.CLIP -> TextOverflow.Clip
    TextOverflowSpec.VISIBLE -> TextOverflow.Visible
    TextOverflowSpec.ELLIPSIS, null -> TextOverflow.Ellipsis
}

/**
 * Interpolates a raw server string against the live binding scope.
 *
 * Used for text that sits outside the component tree — app bar titles, dialog
 * copy — which still needs `{{bindings}}` but has no [ResolvedNode] to carry them.
 */
@Composable
public fun boundText(raw: String?): String? {
    if (raw == null) return null
    val runtime = LocalScreenRuntime.current
    val binding = LocalBindingScope.current
    if (!runtime.expressions.isDynamic(raw)) return raw
    return androidx.compose.runtime.remember(raw, binding) {
        (runtime.expressions.interpolate(raw, binding) as? kotlinx.serialization.json.JsonPrimitive)
            ?.content ?: raw
    }
}
