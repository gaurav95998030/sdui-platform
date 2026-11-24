package com.sdui.demo

import com.sdui.core.model.ColorSchemeSpec
import com.sdui.core.model.ShapeScaleSpec
import com.sdui.core.model.ThemeSpec

/**
 * A local fallback theme.
 *
 * In production this comes from the backend's theme endpoint — the constant here
 * only covers the first frame before that request lands, and offline launches.
 */
object DemoThemes {

    val Brand: ThemeSpec = ThemeSpec(
        id = "brand",
        name = "Demo Brand",
        light = ColorSchemeSpec(
            primary = "#4C5BD4",
            onPrimary = "#FFFFFF",
            primaryContainer = "#E1E3FF",
            onPrimaryContainer = "#0A1160",
            secondary = "#00A97F",
            background = "#F7F8FC",
            surface = "#FFFFFF",
            surfaceVariant = "#EDEFF7",
            onSurfaceVariant = "#5B6076",
            error = "#D93838",
        ),
        dark = ColorSchemeSpec(
            primary = "#B7BDFF",
            onPrimary = "#141B6B",
            primaryContainer = "#2C348F",
            onPrimaryContainer = "#E1E3FF",
            secondary = "#5FE0BA",
            background = "#101116",
            surface = "#171922",
            surfaceVariant = "#2A2D3A",
            onSurfaceVariant = "#C4C7D6",
        ),
        shapes = ShapeScaleSpec(small = 10f, medium = 16f, large = 22f),
        tokens = mapOf(
            "brandGold" to "#E9A23B",
            "success" to "#1FA971",
            "danger" to "#D93838",
            "muted" to "onSurfaceVariant",
        ),
    )
}
