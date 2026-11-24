package com.sdui.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sdui.core.model.AlignmentSpec
import com.sdui.core.model.Corners
import com.sdui.core.model.Dimension
import com.sdui.core.model.EdgeInsets
import com.sdui.core.model.GradientType
import com.sdui.core.model.ModifierSpec
import com.sdui.theme.LocalSduiTheme

/**
 * Translates a [ModifierSpec] into a Compose [Modifier].
 *
 * The application order is fixed and documented, because Compose modifiers are
 * order-sensitive and JSON key order is not meaningful:
 *
 * ```
 * margin -> size -> weight -> aspectRatio -> align -> zIndex -> shadow
 *        -> clip -> background -> border -> scroll -> clickable
 *        -> alpha/rotate/scale -> padding -> testTag/semantics
 * ```
 *
 * That yields the behaviour authors expect: margin outside the background, padding
 * inside it, the ripple clipped to the corner radius, and shadow behind everything.
 */
@Composable
public fun buildModifier(
    spec: ModifierSpec,
    parent: LayoutParent,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier {
    val theme = LocalSduiTheme.current
    val shape: Shape = remember(spec.cornerRadius) { spec.cornerRadius.toShape() }

    var modifier: Modifier = Modifier

    spec.margin?.takeUnless { it.isEmpty }?.let { modifier = modifier.padding(it.toPaddingValues()) }

    // --- size ---
    when (val size = spec.size) {
        Dimension.Fill -> modifier = modifier.fillMaxWidth().fillMaxHeight()
        Dimension.Wrap -> modifier = modifier.wrapContentWidth().wrapContentHeight()
        is Dimension.Points -> modifier = modifier.size(size.dp.dp)
        is Dimension.Fraction -> modifier = modifier.fillMaxWidth(size.value).fillMaxHeight(size.value)
        null -> Unit
    }
    when (val width = spec.width) {
        Dimension.Fill -> modifier = modifier.fillMaxWidth()
        Dimension.Wrap -> modifier = modifier.wrapContentWidth()
        is Dimension.Points -> modifier = modifier.width(width.dp.dp)
        is Dimension.Fraction -> modifier = modifier.fillMaxWidth(width.value)
        null -> Unit
    }
    when (val height = spec.height) {
        Dimension.Fill -> modifier = modifier.fillMaxHeight()
        Dimension.Wrap -> modifier = modifier.wrapContentHeight()
        is Dimension.Points -> modifier = modifier.height(height.dp.dp)
        is Dimension.Fraction -> modifier = modifier.fillMaxHeight(height.value)
        null -> Unit
    }
    if (spec.minWidth != null || spec.minHeight != null) {
        modifier = modifier.defaultMinSize(
            minWidth = spec.minWidth?.dp ?: androidx.compose.ui.unit.Dp.Unspecified,
            minHeight = spec.minHeight?.dp ?: androidx.compose.ui.unit.Dp.Unspecified,
        )
    }
    spec.maxWidth?.let { modifier = modifier.widthIn(max = it.dp) }
    spec.maxHeight?.let { modifier = modifier.heightIn(max = it.dp) }

    // --- flex participation (only meaningful inside row/column) ---
    spec.weight?.takeIf { it > 0f }?.let { weight ->
        modifier = when (parent) {
            is LayoutParent.Row -> with(parent.scope) { modifier.weight(weight) }
            is LayoutParent.Column -> with(parent.scope) { modifier.weight(weight) }
            else -> modifier
        }
    }

    spec.aspectRatio?.takeIf { it > 0f }?.let { modifier = modifier.aspectRatio(it) }

    spec.alignment?.let { alignment ->
        modifier = when (parent) {
            is LayoutParent.Box -> with(parent.scope) { modifier.align(alignment.toAlignment()) }
            is LayoutParent.Row -> with(parent.scope) { modifier.align(alignment.toVerticalAlignment()) }
            is LayoutParent.Column -> with(parent.scope) { modifier.align(alignment.toHorizontalAlignment()) }
            LayoutParent.None -> modifier
        }
    }

    spec.zIndex?.let { modifier = modifier.zIndex(it) }

    // --- surface ---
    val elevation = spec.elevation ?: 0f
    if (elevation > 0f) modifier = modifier.shadow(elevation.dp, shape, clip = false)
    if (spec.clip && spec.cornerRadius?.isEmpty == false) modifier = modifier.clip(shape)

    val gradient = spec.backgroundGradient?.takeIf { it.colors.size >= 2 }
    if (gradient != null) {
        val colors = gradient.colors.map { theme.color(it, Color.Transparent) }
        val brush = when (gradient.type) {
            GradientType.VERTICAL -> Brush.verticalGradient(colors)
            GradientType.HORIZONTAL -> Brush.horizontalGradient(colors)
            GradientType.RADIAL -> Brush.radialGradient(colors)
            GradientType.SWEEP -> Brush.sweepGradient(colors)
        }
        modifier = modifier.background(brush, shape)
    } else {
        theme.color(spec.background)?.let { modifier = modifier.background(it, shape) }
    }

    spec.border?.let { border ->
        modifier = modifier.border(
            width = border.width.dp,
            color = theme.color(border.color, Color.Transparent),
            shape = border.cornerRadius?.toShape() ?: shape,
        )
    }

    if (spec.scrollable) modifier = modifier.verticalScroll(rememberScrollState())

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled && spec.enabled,
            onClick = onClick,
        )
    }

    spec.alpha?.let { modifier = modifier.alpha(it.coerceIn(0f, 1f)) }
    spec.rotation?.let { modifier = modifier.rotate(it) }
    spec.scale?.let { modifier = modifier.scale(it) }

    spec.padding?.takeUnless { it.isEmpty }?.let { modifier = modifier.padding(it.toPaddingValues()) }

    spec.testTag?.let { modifier = modifier.testTag(it) }
    spec.contentDescription?.let { description ->
        modifier = modifier.semantics { contentDescription = description }
    }

    return modifier
}

public fun EdgeInsets.toPaddingValues(): androidx.compose.foundation.layout.PaddingValues =
    androidx.compose.foundation.layout.PaddingValues(
        start = start.dp, top = top.dp, end = end.dp, bottom = bottom.dp,
    )

public fun Corners?.toShape(): Shape = when {
    this == null || isEmpty -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(
        topStart = topStart.dp,
        topEnd = topEnd.dp,
        bottomEnd = bottomEnd.dp,
        bottomStart = bottomStart.dp,
    )
}

public fun AlignmentSpec.toAlignment(): Alignment = when (this) {
    AlignmentSpec.TOP_START -> Alignment.TopStart
    AlignmentSpec.TOP_CENTER -> Alignment.TopCenter
    AlignmentSpec.TOP_END -> Alignment.TopEnd
    AlignmentSpec.CENTER_START -> Alignment.CenterStart
    AlignmentSpec.CENTER -> Alignment.Center
    AlignmentSpec.CENTER_END -> Alignment.CenterEnd
    AlignmentSpec.BOTTOM_START -> Alignment.BottomStart
    AlignmentSpec.BOTTOM_CENTER -> Alignment.BottomCenter
    AlignmentSpec.BOTTOM_END -> Alignment.BottomEnd
}

public fun AlignmentSpec.toVerticalAlignment(): Alignment.Vertical = when (this) {
    AlignmentSpec.TOP_START, AlignmentSpec.TOP_CENTER, AlignmentSpec.TOP_END -> Alignment.Top
    AlignmentSpec.BOTTOM_START, AlignmentSpec.BOTTOM_CENTER, AlignmentSpec.BOTTOM_END -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

public fun AlignmentSpec.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    AlignmentSpec.TOP_START, AlignmentSpec.CENTER_START, AlignmentSpec.BOTTOM_START -> Alignment.Start
    AlignmentSpec.TOP_END, AlignmentSpec.CENTER_END, AlignmentSpec.BOTTOM_END -> Alignment.End
    else -> Alignment.CenterHorizontally
}

internal val UnspecifiedOffset = Offset.Unspecified
