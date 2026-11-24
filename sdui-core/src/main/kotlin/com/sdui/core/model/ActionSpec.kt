package com.sdui.core.model

import com.sdui.core.json.EmptyJsonObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A server-described side effect. [type] is resolved through the action registry,
 * so hosts can plug in `"biometricAuth"` or `"scanQR"` without touching the SDK.
 *
 * ```json
 * {
 *   "type": "apiCall",
 *   "params": { "api": "submitExpense" },
 *   "then": [ { "type": "showToast", "params": { "message": "Saved" } },
 *             { "type": "navigate", "params": { "screen": "home", "mode": "popToRoot" } } ]
 * }
 * ```
 */
@Serializable
public data class ActionSpec(
    val type: String,
    val params: JsonObject = EmptyJsonObject,

    /** Guard expression; when present and falsy, [otherwise] runs instead. */
    @SerialName("if") val condition: String? = null,

    /** Runs after this action succeeds. */
    val then: List<ActionSpec> = emptyList(),

    /** Runs when [condition] is falsy, or when this action fails. */
    @SerialName("else") val otherwise: List<ActionSpec> = emptyList(),

    /** Runs when this action throws, regardless of [condition]. */
    val onError: List<ActionSpec> = emptyList(),

    val debounceMs: Long = 0,

    /** When true, the chain continues even if this action fails. */
    val ignoreFailure: Boolean = false,
)

/** Built-in action type identifiers. Custom types are plain strings. */
public object ActionTypes {
    public const val NAVIGATE: String = "navigate"
    public const val API_CALL: String = "apiCall"
    public const val SHOW_TOAST: String = "showToast"
    public const val SHOW_DIALOG: String = "showDialog"
    public const val BOTTOM_SHEET: String = "bottomSheet"
    public const val DISMISS: String = "dismiss"
    public const val SHARE: String = "share"
    public const val COPY_CLIPBOARD: String = "copyClipboard"
    public const val OPEN_URL: String = "openUrl"
    public const val OPEN_NATIVE_SCREEN: String = "openNativeScreen"
    public const val SCAN_QR: String = "scanQR"
    public const val CAMERA: String = "camera"
    public const val ANALYTICS: String = "analytics"
    public const val SET_STATE: String = "setState"
    public const val TOGGLE_STATE: String = "toggleState"
    public const val CLEAR_STATE: String = "clearState"
    public const val VALIDATE_FORM: String = "validateForm"
    public const val SUBMIT_FORM: String = "submitForm"
    public const val REFRESH_SCREEN: String = "refreshScreen"
    public const val SET_THEME: String = "setTheme"
    public const val SEQUENCE: String = "sequence"
    public const val DELAY: String = "delay"
    public const val NOOP: String = "noop"
}
