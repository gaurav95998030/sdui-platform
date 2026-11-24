package com.sdui.core.model

import kotlinx.serialization.Serializable

/**
 * The server-driven equivalent of a Compose `Modifier` chain.
 *
 * Order of application in the renderer is fixed and documented so that output is
 * deterministic regardless of JSON key order:
 *
 * `margin -> size -> weight -> aspectRatio -> background+corner -> border -> clip
 *  -> shadow -> clickable -> alpha/rotate -> padding -> testTag`
 */
@Serializable
public data class ModifierSpec(
    val padding: EdgeInsets? = null,
    val margin: EdgeInsets? = null,
    val background: String? = null,
    val backgroundGradient: GradientSpec? = null,
    val cornerRadius: Corners? = null,
    val elevation: Float? = null,
    val border: BorderSpec? = null,
    val width: Dimension? = null,
    val height: Dimension? = null,
    val size: Dimension? = null,
    val minWidth: Float? = null,
    val minHeight: Float? = null,
    val maxWidth: Float? = null,
    val maxHeight: Float? = null,
    val weight: Float? = null,
    val alignment: AlignmentSpec? = null,
    val aspectRatio: Float? = null,
    val alpha: Float? = null,
    val rotation: Float? = null,
    val scale: Float? = null,
    val clip: Boolean = true,
    val clickable: Boolean? = null,
    val enabled: Boolean = true,
    val scrollable: Boolean = false,
    val zIndex: Float? = null,
    val testTag: String? = null,
    val contentDescription: String? = null,
) {
    public companion object {
        public val None: ModifierSpec = ModifierSpec()
    }
}

@Serializable
public data class BorderSpec(
    val width: Float = 1f,
    val color: String = "#00000022",
    val cornerRadius: Corners? = null,
)

@Serializable
public data class GradientSpec(
    val colors: List<String> = emptyList(),
    val type: GradientType = GradientType.VERTICAL,
    val stops: List<Float> = emptyList(),
)

@Serializable
public enum class GradientType {
    @kotlinx.serialization.SerialName("vertical") VERTICAL,
    @kotlinx.serialization.SerialName("horizontal") HORIZONTAL,
    @kotlinx.serialization.SerialName("radial") RADIAL,
    @kotlinx.serialization.SerialName("sweep") SWEEP,
}

/**
 * Visual/typographic styling. Values are resolved against the active theme, so
 * `textColor` accepts both literals (`"#FF0000"`) and tokens (`"primary"`,
 * `"onSurfaceVariant"`), and `typography` accepts Material role names.
 */
@Serializable
public data class StyleSpec(
    val textColor: String? = null,
    val fontSize: Float? = null,
    val fontWeight: FontWeightSpec? = null,
    val italic: Boolean = false,
    val fontFamily: String? = null,
    val letterSpacing: Float? = null,
    val lineHeight: Float? = null,
    val textAlign: TextAlignSpec? = null,
    val decoration: TextDecorationSpec? = null,
    val maxLines: Int? = null,
    val overflow: TextOverflowSpec? = null,
    val typography: String? = null,
    val variant: String? = null,
    val tint: String? = null,
    val containerColor: String? = null,
    val contentColor: String? = null,
) {
    public companion object {
        public val None: StyleSpec = StyleSpec()
    }
}

// ---------------------------------------------------------------------------
// Animation
// ---------------------------------------------------------------------------

@Serializable
public enum class AnimationType {
    @kotlinx.serialization.SerialName("none") NONE,
    @kotlinx.serialization.SerialName("fade") FADE,
    @kotlinx.serialization.SerialName("slideUp") SLIDE_UP,
    @kotlinx.serialization.SerialName("slideDown") SLIDE_DOWN,
    @kotlinx.serialization.SerialName("slideStart") SLIDE_START,
    @kotlinx.serialization.SerialName("slideEnd") SLIDE_END,
    @kotlinx.serialization.SerialName("scale") SCALE,
    @kotlinx.serialization.SerialName("expand") EXPAND,
    @kotlinx.serialization.SerialName("collapse") COLLAPSE,
    @kotlinx.serialization.SerialName("shimmer") SHIMMER,
}

@Serializable
public enum class EasingSpec {
    @kotlinx.serialization.SerialName("standard") STANDARD,
    @kotlinx.serialization.SerialName("linear") LINEAR,
    @kotlinx.serialization.SerialName("easeIn") EASE_IN,
    @kotlinx.serialization.SerialName("easeOut") EASE_OUT,
    @kotlinx.serialization.SerialName("easeInOut") EASE_IN_OUT,
    @kotlinx.serialization.SerialName("bounce") BOUNCE,
}

/**
 * Declarative appear/disappear animation. `enter` runs when the node becomes
 * visible (mount, or `visible` flipping to true) and `exit` on removal.
 */
@Serializable
public data class AnimationSpec(
    val enter: AnimationType = AnimationType.FADE,
    val exit: AnimationType = AnimationType.FADE,
    val durationMs: Int = 300,
    val delayMs: Int = 0,
    val easing: EasingSpec = EasingSpec.STANDARD,
    val repeat: Boolean = false,
)
