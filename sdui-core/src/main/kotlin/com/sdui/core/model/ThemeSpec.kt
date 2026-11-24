package com.sdui.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class ThemeMode {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

/**
 * A complete, server-owned design system. Shipping a new [ThemeSpec] restyles
 * every screen without an app release: colors, type ramp, spacing scale and
 * corner geometry all come from here.
 */
@Serializable
public data class ThemeSpec(
    val id: String = "default",
    val name: String? = null,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val light: ColorSchemeSpec = ColorSchemeSpec.LightDefaults,
    val dark: ColorSchemeSpec = ColorSchemeSpec.DarkDefaults,
    val typography: Map<String, TypographyStyleSpec> = emptyMap(),
    val spacing: Map<String, Float> = DEFAULT_SPACING,
    val shapes: ShapeScaleSpec = ShapeScaleSpec(),
    /** Arbitrary named colors usable anywhere a color is accepted (`"brandGold"`). */
    val tokens: Map<String, String> = emptyMap(),
) {
    public companion object {
        public val DEFAULT_SPACING: Map<String, Float> = mapOf(
            "none" to 0f, "xs" to 4f, "sm" to 8f, "md" to 16f,
            "lg" to 24f, "xl" to 32f, "xxl" to 48f,
        )
        public val Default: ThemeSpec = ThemeSpec()
    }
}

/** Material 3 color roles. Any `null` role falls back to the built-in baseline. */
@Serializable
public data class ColorSchemeSpec(
    val primary: String? = null,
    val onPrimary: String? = null,
    val primaryContainer: String? = null,
    val onPrimaryContainer: String? = null,
    val secondary: String? = null,
    val onSecondary: String? = null,
    val secondaryContainer: String? = null,
    val onSecondaryContainer: String? = null,
    val tertiary: String? = null,
    val onTertiary: String? = null,
    val background: String? = null,
    val onBackground: String? = null,
    val surface: String? = null,
    val onSurface: String? = null,
    val surfaceVariant: String? = null,
    val onSurfaceVariant: String? = null,
    val surfaceContainer: String? = null,
    val error: String? = null,
    val onError: String? = null,
    val errorContainer: String? = null,
    val outline: String? = null,
    val outlineVariant: String? = null,
    val scrim: String? = null,
) {
    public companion object {
        public val LightDefaults: ColorSchemeSpec = ColorSchemeSpec()
        public val DarkDefaults: ColorSchemeSpec = ColorSchemeSpec()
    }
}

@Serializable
public data class TypographyStyleSpec(
    val fontSize: Float? = null,
    val lineHeight: Float? = null,
    val letterSpacing: Float? = null,
    val fontWeight: FontWeightSpec? = null,
    val fontFamily: String? = null,
)

@Serializable
public data class ShapeScaleSpec(
    val extraSmall: Float = 4f,
    val small: Float = 8f,
    val medium: Float = 12f,
    val large: Float = 16f,
    val extraLarge: Float = 28f,
)
