package com.sdui.components

import androidx.compose.ui.text.input.KeyboardType
import com.sdui.renderer.ComponentRegistry

/**
 * The component vocabulary an SDUI instance starts with.
 *
 * Every entry goes through the same [ComponentRegistry] a host uses for its own
 * components — there is no privileged built-in path — so overriding `"text"` with
 * a brand-specific renderer is a single `register(..., override = true)` call.
 */
public object DefaultComponents {

    public fun createRegistry(): ComponentRegistry = ComponentRegistry().apply {
        // --- layout ---
        register("column", ColumnRenderer)
        register("row", RowRenderer)
        register("box", BoxRenderer)
        register("card", CardRenderer)
        register("surface", SurfaceRenderer)

        // --- content ---
        register("text", TextRenderer)
        register("image", ImageRenderer)
        register("icon", IconRenderer)
        register("spacer", SpacerRenderer)
        register("divider", DividerRenderer)

        // --- interaction ---
        register("button", ButtonRenderer)
        register("iconButton", IconButtonRenderer)
        register("chip", ChipRenderer)

        // --- collections ---
        register("lazyColumn", LazyColumnRenderer)
        register("lazyRow", LazyRowRenderer)
        register("grid", GridRenderer)

        // --- forms ---
        register("textField", TextFieldRenderer())
        register("password", TextFieldRenderer(KeyboardType.Password, defaultSecure = true))
        register("number", TextFieldRenderer(KeyboardType.Number))
        register("otp", OtpRenderer)
        register("checkbox", CheckboxRenderer)
        register("switch", SwitchRenderer)
        register("radioGroup", RadioGroupRenderer)
        register("dropdown", DropdownRenderer)
        register("datePicker", DatePickerRenderer)

        // --- feedback ---
        register("progress", ProgressRenderer)
        register("skeleton", SkeletonRenderer)
        register("badge", BadgeRenderer)
        register("empty", EmptyStateRenderer)

        // Aliases keep dashboard vocabulary forgiving without duplicating renderers.
        alias("vstack", "column")
        alias("hstack", "row")
        alias("stack", "box")
        alias("label", "text")
        alias("img", "image")
        alias("list", "lazyColumn")
        alias("carousel", "lazyRow")
        alias("input", "textField")
        alias("select", "dropdown")
        alias("toggle", "switch")
        alias("loader", "progress")
        alias("shimmer", "skeleton")
        alias("emptyState", "empty")
    }
}
