package com.sdui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Turns the strings screens use for colors into [Color].
 *
 * Three forms are accepted, in this order:
 * 1. **Literals** — `#RGB`, `#RRGGBB`, `#AARRGGBB`, `transparent`.
 * 2. **Material roles** — `primary`, `onSurfaceVariant`, … resolved against the
 *    active [ColorScheme], so a theme swap restyles every screen at once.
 * 3. **Custom tokens** — anything in `ThemeSpec.tokens`, e.g. `brandGold`.
 *
 * Unknown input returns `null`; callers substitute a sensible default rather than
 * rendering an arbitrary color.
 */
public object ColorResolver {

    public fun resolve(
        raw: String?,
        scheme: ColorScheme,
        tokens: Map<String, String> = emptyMap(),
        depth: Int = 0,
    ): Color? {
        if (raw.isNullOrBlank() || depth > 4) return null
        val value = raw.trim()

        if (value.startsWith("#")) return parseHex(value)
        if (value.startsWith("0x", ignoreCase = true)) return parseHex("#" + value.drop(2))

        // "@token" / "$token" prefixes are accepted for authors who prefer them.
        val name = value.removePrefix("@").removePrefix("$").removePrefix("color/")

        role(name, scheme)?.let { return it }
        namedLiteral(name)?.let { return it }

        // Custom tokens may themselves point at a role or another token.
        tokens[name]?.let { return resolve(it, scheme, tokens, depth + 1) }
        tokens.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.let { return resolve(it.value, scheme, tokens, depth + 1) }

        return null
    }

    public fun resolveOr(
        raw: String?,
        fallback: Color,
        scheme: ColorScheme,
        tokens: Map<String, String> = emptyMap(),
    ): Color = resolve(raw, scheme, tokens) ?: fallback

    private fun role(name: String, scheme: ColorScheme): Color? = when (name) {
        "primary" -> scheme.primary
        "onPrimary" -> scheme.onPrimary
        "primaryContainer" -> scheme.primaryContainer
        "onPrimaryContainer" -> scheme.onPrimaryContainer
        "inversePrimary" -> scheme.inversePrimary
        "secondary" -> scheme.secondary
        "onSecondary" -> scheme.onSecondary
        "secondaryContainer" -> scheme.secondaryContainer
        "onSecondaryContainer" -> scheme.onSecondaryContainer
        "tertiary" -> scheme.tertiary
        "onTertiary" -> scheme.onTertiary
        "tertiaryContainer" -> scheme.tertiaryContainer
        "onTertiaryContainer" -> scheme.onTertiaryContainer
        "background" -> scheme.background
        "onBackground" -> scheme.onBackground
        "surface" -> scheme.surface
        "onSurface" -> scheme.onSurface
        "surfaceVariant" -> scheme.surfaceVariant
        "onSurfaceVariant" -> scheme.onSurfaceVariant
        "surfaceTint" -> scheme.surfaceTint
        "surfaceContainer" -> scheme.surfaceContainer
        "surfaceContainerHigh" -> scheme.surfaceContainerHigh
        "surfaceContainerHighest" -> scheme.surfaceContainerHighest
        "surfaceContainerLow" -> scheme.surfaceContainerLow
        "surfaceContainerLowest" -> scheme.surfaceContainerLowest
        "inverseSurface" -> scheme.inverseSurface
        "inverseOnSurface" -> scheme.inverseOnSurface
        "error" -> scheme.error
        "onError" -> scheme.onError
        "errorContainer" -> scheme.errorContainer
        "onErrorContainer" -> scheme.onErrorContainer
        "outline" -> scheme.outline
        "outlineVariant" -> scheme.outlineVariant
        "scrim" -> scheme.scrim
        else -> null
    }

    private fun namedLiteral(name: String): Color? = when (name.lowercase()) {
        "transparent", "clear", "none" -> Color.Transparent
        "black" -> Color.Black
        "white" -> Color.White
        "red" -> Color(0xFFE53935)
        "green" -> Color(0xFF43A047)
        "blue" -> Color(0xFF1E88E5)
        "yellow" -> Color(0xFFFDD835)
        "orange" -> Color(0xFFFB8C00)
        "purple" -> Color(0xFF8E24AA)
        "gray", "grey" -> Color(0xFF9E9E9E)
        else -> null
    }

    private fun parseHex(value: String): Color? {
        val hex = value.removePrefix("#")
        val normalized = when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            4 -> hex.drop(1).map { "$it$it" }.joinToString("").let { rgb ->
                val alpha = hex.first().let { "$it$it" }
                alpha + rgb
            }
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        // Parsed as Long because 0xFFxxxxxx overflows a signed Int; toInt() then
        // keeps the exact ARGB bit pattern Color(Int) expects.
        val argb = normalized.toLongOrNull(16) ?: return null
        return Color(argb.toInt())
    }
}
