package com.sdui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdui.core.model.FontWeightSpec
import com.sdui.core.model.ThemeMode
import com.sdui.core.model.ThemeSpec
import com.sdui.core.model.TypographyStyleSpec
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Everything a renderer needs to turn style strings into Compose values.
 * Held in a [staticCompositionLocalOf] because a theme change should restart
 * composition wholesale rather than trickle through recomposition scopes.
 */
@Immutable
public data class SduiThemeContext(
    val spec: ThemeSpec,
    val isDark: Boolean,
    val colorScheme: ColorScheme,
    val tokens: Map<String, String>,
    val spacing: Map<String, Float>,
) {
    public fun color(raw: String?): Color? = ColorResolver.resolve(raw, colorScheme, tokens)

    public fun color(raw: String?, fallback: Color): Color =
        ColorResolver.resolve(raw, colorScheme, tokens) ?: fallback

    /** Accepts a number (`16`) or a scale token (`"md"`). */
    public fun spacing(raw: String?, fallback: Float = 0f): Dp =
        (raw?.toFloatOrNull() ?: spacing[raw] ?: fallback).dp
}

public val LocalSduiTheme: androidx.compose.runtime.ProvidableCompositionLocal<SduiThemeContext> =
    staticCompositionLocalOf {
        error("No SduiThemeContext provided. Wrap your content in SduiTheme { }.")
    }

/**
 * Applies a server-supplied design system.
 *
 * Resolution order for the color scheme: dynamic color (Android 12+, when the
 * theme opts in) -> the spec's light/dark roles -> the Material baseline. Any role
 * the server omits keeps its baseline value, so a partial theme is always valid.
 */
@Composable
public fun SduiTheme(
    spec: ThemeSpec,
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = darkOverride ?: when (spec.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = remember(spec, isDark) {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        val dynamic = if (spec.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            base
        }
        dynamic.applying(if (isDark) spec.dark else spec.light, spec.tokens)
    }

    val themeContext = remember(spec, isDark, colorScheme) {
        SduiThemeContext(
            spec = spec,
            isDark = isDark,
            colorScheme = colorScheme,
            tokens = spec.tokens,
            spacing = spec.spacing.ifEmpty { ThemeSpec.DEFAULT_SPACING },
        )
    }

    val typography = remember(spec) { buildTypography(spec.typography) }
    val shapes = remember(spec) {
        Shapes(
            extraSmall = RoundedCornerShape(spec.shapes.extraSmall.dp),
            small = RoundedCornerShape(spec.shapes.small.dp),
            medium = RoundedCornerShape(spec.shapes.medium.dp),
            large = RoundedCornerShape(spec.shapes.large.dp),
            extraLarge = RoundedCornerShape(spec.shapes.extraLarge.dp),
        )
    }

    CompositionLocalProvider(LocalSduiTheme provides themeContext) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/** Overlays the non-null roles of [overrides] onto this scheme. */
private fun ColorScheme.applying(
    overrides: com.sdui.core.model.ColorSchemeSpec,
    tokens: Map<String, String>,
): ColorScheme {
    fun pick(raw: String?, current: Color): Color =
        ColorResolver.resolve(raw, this, tokens) ?: current

    return copy(
        primary = pick(overrides.primary, primary),
        onPrimary = pick(overrides.onPrimary, onPrimary),
        primaryContainer = pick(overrides.primaryContainer, primaryContainer),
        onPrimaryContainer = pick(overrides.onPrimaryContainer, onPrimaryContainer),
        secondary = pick(overrides.secondary, secondary),
        onSecondary = pick(overrides.onSecondary, onSecondary),
        secondaryContainer = pick(overrides.secondaryContainer, secondaryContainer),
        onSecondaryContainer = pick(overrides.onSecondaryContainer, onSecondaryContainer),
        tertiary = pick(overrides.tertiary, tertiary),
        onTertiary = pick(overrides.onTertiary, onTertiary),
        background = pick(overrides.background, background),
        onBackground = pick(overrides.onBackground, onBackground),
        surface = pick(overrides.surface, surface),
        onSurface = pick(overrides.onSurface, onSurface),
        surfaceVariant = pick(overrides.surfaceVariant, surfaceVariant),
        onSurfaceVariant = pick(overrides.onSurfaceVariant, onSurfaceVariant),
        surfaceContainer = pick(overrides.surfaceContainer, surfaceContainer),
        error = pick(overrides.error, error),
        onError = pick(overrides.onError, onError),
        errorContainer = pick(overrides.errorContainer, errorContainer),
        outline = pick(overrides.outline, outline),
        outlineVariant = pick(overrides.outlineVariant, outlineVariant),
        scrim = pick(overrides.scrim, scrim),
    )
}

/** Maps Material role names in the spec onto the Material 3 type scale. */
private fun buildTypography(overrides: Map<String, TypographyStyleSpec>): Typography {
    val base = Typography()
    if (overrides.isEmpty()) return base

    fun style(name: String, current: TextStyle): TextStyle {
        val spec = overrides[name] ?: return current
        return current.copy(
            fontSize = spec.fontSize?.sp ?: current.fontSize,
            lineHeight = spec.lineHeight?.sp ?: current.lineHeight,
            letterSpacing = spec.letterSpacing?.sp ?: current.letterSpacing,
            fontWeight = spec.fontWeight?.toCompose() ?: current.fontWeight,
        )
    }

    return base.copy(
        displayLarge = style("displayLarge", base.displayLarge),
        displayMedium = style("displayMedium", base.displayMedium),
        displaySmall = style("displaySmall", base.displaySmall),
        headlineLarge = style("headlineLarge", base.headlineLarge),
        headlineMedium = style("headlineMedium", base.headlineMedium),
        headlineSmall = style("headlineSmall", base.headlineSmall),
        titleLarge = style("titleLarge", base.titleLarge),
        titleMedium = style("titleMedium", base.titleMedium),
        titleSmall = style("titleSmall", base.titleSmall),
        bodyLarge = style("bodyLarge", base.bodyLarge),
        bodyMedium = style("bodyMedium", base.bodyMedium),
        bodySmall = style("bodySmall", base.bodySmall),
        labelLarge = style("labelLarge", base.labelLarge),
        labelMedium = style("labelMedium", base.labelMedium),
        labelSmall = style("labelSmall", base.labelSmall),
    )
}

public fun FontWeightSpec.toCompose(): FontWeight = when (this) {
    FontWeightSpec.THIN -> FontWeight.Thin
    FontWeightSpec.LIGHT -> FontWeight.Light
    FontWeightSpec.NORMAL -> FontWeight.Normal
    FontWeightSpec.MEDIUM -> FontWeight.Medium
    FontWeightSpec.SEMI_BOLD -> FontWeight.SemiBold
    FontWeightSpec.BOLD -> FontWeight.Bold
    FontWeightSpec.EXTRA_BOLD -> FontWeight.ExtraBold
    FontWeightSpec.BLACK -> FontWeight.Black
}

/** Resolves a Material type-scale role name to its [TextStyle]. */
@Composable
public fun typographyRole(name: String?): TextStyle? {
    val t = MaterialTheme.typography
    return when (name) {
        "displayLarge" -> t.displayLarge
        "displayMedium" -> t.displayMedium
        "displaySmall" -> t.displaySmall
        "headlineLarge" -> t.headlineLarge
        "headlineMedium" -> t.headlineMedium
        "headlineSmall" -> t.headlineSmall
        "titleLarge" -> t.titleLarge
        "titleMedium" -> t.titleMedium
        "titleSmall" -> t.titleSmall
        "bodyLarge" -> t.bodyLarge
        "bodyMedium" -> t.bodyMedium
        "bodySmall" -> t.bodySmall
        "labelLarge" -> t.labelLarge
        "labelMedium" -> t.labelMedium
        "labelSmall" -> t.labelSmall
        else -> null
    }
}

internal val ItalicStyle = FontStyle.Italic
