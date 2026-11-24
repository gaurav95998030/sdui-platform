package com.sdui.actions

import com.sdui.actions.handlers.AnalyticsHandler
import com.sdui.actions.handlers.ApiCallHandler
import com.sdui.actions.handlers.BackHandler
import com.sdui.actions.handlers.BottomSheetHandler
import com.sdui.actions.handlers.ClearStateHandler
import com.sdui.actions.handlers.CopyClipboardHandler
import com.sdui.actions.handlers.DelayHandler
import com.sdui.actions.handlers.DismissHandler
import com.sdui.actions.handlers.NativeCapabilityHandler
import com.sdui.actions.handlers.NavigateHandler
import com.sdui.actions.handlers.NoopHandler
import com.sdui.actions.handlers.OpenUrlHandler
import com.sdui.actions.handlers.RefreshScreenHandler
import com.sdui.actions.handlers.SequenceHandler
import com.sdui.actions.handlers.SetStateHandler
import com.sdui.actions.handlers.SetThemeHandler
import com.sdui.actions.handlers.ShareHandler
import com.sdui.actions.handlers.ShowDialogHandler
import com.sdui.actions.handlers.ShowToastHandler
import com.sdui.actions.handlers.SubmitFormHandler
import com.sdui.actions.handlers.ToggleStateHandler
import com.sdui.actions.handlers.ValidateFormHandler
import com.sdui.core.model.ActionTypes

/**
 * The action vocabulary every SDUI instance starts with.
 *
 * Registration order does not matter and nothing here is special-cased by the
 * dispatcher — the built-ins go through the exact same registry a host uses for
 * its own actions, which is the property that keeps the extension point honest.
 */
public object DefaultActions {

    public fun createRegistry(): ActionRegistry = ActionRegistry().apply {
        val apiHandler = ApiCallHandler()

        // --- navigation & lifecycle ---
        register(ActionTypes.NAVIGATE, NavigateHandler)
        register(ActionTypes.REFRESH_SCREEN, RefreshScreenHandler)
        register(ActionTypes.SET_THEME, SetThemeHandler)

        // --- state ---
        register(ActionTypes.SET_STATE, SetStateHandler)
        register(ActionTypes.TOGGLE_STATE, ToggleStateHandler)
        register(ActionTypes.CLEAR_STATE, ClearStateHandler)

        // --- network & forms ---
        register(ActionTypes.API_CALL, apiHandler)
        register(ActionTypes.VALIDATE_FORM, ValidateFormHandler)
        register(ActionTypes.SUBMIT_FORM, SubmitFormHandler(apiHandler))

        // --- overlays & system ---
        register(ActionTypes.SHOW_TOAST, ShowToastHandler)
        register(ActionTypes.SHOW_DIALOG, ShowDialogHandler)
        register(ActionTypes.BOTTOM_SHEET, BottomSheetHandler)
        register(ActionTypes.DISMISS, DismissHandler)
        register(ActionTypes.SHARE, ShareHandler)
        register(ActionTypes.COPY_CLIPBOARD, CopyClipboardHandler)
        register(ActionTypes.OPEN_URL, OpenUrlHandler)
        register(ActionTypes.ANALYTICS, AnalyticsHandler)

        // --- control flow ---
        register(ActionTypes.SEQUENCE, SequenceHandler)
        register(ActionTypes.DELAY, DelayHandler)
        register(ActionTypes.NOOP, NoopHandler)

        // --- host capabilities ---
        // These resolve through NativeCapabilityRegistry, so the SDK ships the
        // vocabulary while the app owns the implementation and its permissions.
        register(ActionTypes.OPEN_NATIVE_SCREEN, NativeCapabilityHandler())
        register(ActionTypes.SCAN_QR, NativeCapabilityHandler(ActionTypes.SCAN_QR))
        register(ActionTypes.CAMERA, NativeCapabilityHandler(ActionTypes.CAMERA))

        // `back` is its own handler rather than an alias of navigate, because the
        // pop path takes no target screen and must succeed with an empty params bag.
        register("back", BackHandler)

        // Common aliases so dashboard authors are not tripped by casing/naming.
        alias("goBack", "back")
        alias("pop", "back")
        alias("toast", ActionTypes.SHOW_TOAST)
        alias("dialog", ActionTypes.SHOW_DIALOG)
        alias("sheet", ActionTypes.BOTTOM_SHEET)
        alias("api", ActionTypes.API_CALL)
        alias("track", ActionTypes.ANALYTICS)
        alias("setValue", ActionTypes.SET_STATE)
    }
}
