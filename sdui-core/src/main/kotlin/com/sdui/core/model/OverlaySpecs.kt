package com.sdui.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class ToastDuration {
    @SerialName("short") SHORT,
    @SerialName("long") LONG,
}

@Serializable
public data class ToastSpec(
    val message: String = "",
    val duration: ToastDuration = ToastDuration.SHORT,
    /** Renders as a Material snackbar inside the SDUI host instead of a system toast. */
    val snackbar: Boolean = false,
    val actionLabel: String? = null,
    val action: ActionSpec? = null,
)

/**
 * A dialog described entirely by the server. [content] lets an author drop an
 * arbitrary component tree into the body when title/message is not enough.
 */
@Serializable
public data class DialogSpec(
    val title: String? = null,
    val message: String? = null,
    val content: SduiNode? = null,
    val confirmLabel: String? = "OK",
    val cancelLabel: String? = null,
    val confirmAction: ActionSpec? = null,
    val cancelAction: ActionSpec? = null,
    val dismissible: Boolean = true,
    val destructive: Boolean = false,
)

@Serializable
public data class SheetSpec(
    val title: String? = null,
    /** Inline content; mutually exclusive with [screen]. */
    val content: SduiNode? = null,
    /** Presents a whole published screen as a sheet. */
    val screen: String? = null,
    val showDragHandle: Boolean = true,
    val fullHeight: Boolean = false,
    val dismissible: Boolean = true,
)
