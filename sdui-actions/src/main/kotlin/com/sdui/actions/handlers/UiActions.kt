package com.sdui.actions.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService
import com.sdui.actions.ActionContext
import com.sdui.actions.ActionHandler
import com.sdui.actions.ResolvedAction
import com.sdui.actions.UiEffect
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.json.SduiJson
import com.sdui.core.model.DialogSpec
import com.sdui.core.model.SheetSpec
import com.sdui.core.model.ToastSpec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** `showToast` — a transient message, either a system toast or an in-app snackbar. */
public object ShowToastHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val spec = SduiJson.decodeFromJsonElement(ToastSpec.serializer(), action.params)
        if (spec.message.isBlank()) return SduiResult.success(null)
        context.effects.emit(UiEffect.ShowToast(spec))
        return SduiResult.success(JsonPrimitive(spec.message))
    }
}

/** `showDialog` — title/message/buttons, or an arbitrary component tree as the body. */
public object ShowDialogHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val spec = SduiJson.decodeFromJsonElement(DialogSpec.serializer(), action.params)
        context.effects.emit(UiEffect.ShowDialog(spec))
        return SduiResult.success(null)
    }
}

/**
 * `bottomSheet` — inline content stays an overlay effect; a `screen` reference
 * goes through the navigator so it gets its own state scope and back handling.
 */
public object BottomSheetHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val spec = SduiJson.decodeFromJsonElement(SheetSpec.serializer(), action.params)
        if (spec.screen != null) {
            context.navigator.navigate(
                com.sdui.navigation.NavigationRequest(
                    screenId = spec.screen,
                    mode = com.sdui.core.model.NavigationMode.BOTTOM_SHEET,
                    args = action.obj("args") ?: com.sdui.core.json.EmptyJsonObject,
                ),
            )
        } else {
            context.effects.emit(UiEffect.ShowSheet(spec))
        }
        return SduiResult.success(null)
    }
}

/** `dismiss` — closes the topmost overlay, falling back to a back navigation. */
public object DismissHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        when (action.string("target")) {
            "dialog" -> context.effects.emit(UiEffect.DismissDialog)
            "sheet" -> context.effects.emit(UiEffect.DismissSheet)
            "keyboard" -> context.effects.emit(UiEffect.HideKeyboard)
            else -> {
                context.effects.emit(UiEffect.DismissDialog)
                context.effects.emit(UiEffect.DismissSheet)
                if (!context.navigator.dismissOverlay()) context.navigator.pop()
            }
        }
        return SduiResult.success(null)
    }
}

/** `openUrl` — hands a URL to the system browser or a registered deep-link handler. */
public object OpenUrlHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val url = action.string("url").orEmpty()
        if (url.isBlank()) {
            return SduiResult.failure(SduiError.ActionFailed("openUrl", "Missing 'url' parameter"))
        }
        return SduiResult.catching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.androidContext.startActivity(intent)
            JsonPrimitive(url)
        }
    }
}

/** `share` — the platform share sheet. */
public object ShareHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val text = action.string("text") ?: action.string("url").orEmpty()
        if (text.isBlank()) {
            return SduiResult.failure(SduiError.ActionFailed("share", "Nothing to share"))
        }
        return SduiResult.catching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = action.string("mimeType", "text/plain")
                putExtra(Intent.EXTRA_TEXT, text)
                action.string("subject")?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            }
            val chooser = Intent.createChooser(send, action.string("title"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.androidContext.startActivity(chooser)
            JsonPrimitive(text)
        }
    }
}

/** `copyClipboard` — copies text and (optionally) confirms with a toast. */
public object CopyClipboardHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val text = action.string("text").orEmpty()
        val clipboard = context.androidContext.getSystemService<ClipboardManager>()
            ?: return SduiResult.failure(SduiError.ActionFailed("copyClipboard", "Clipboard unavailable"))
        clipboard.setPrimaryClip(ClipData.newPlainText(action.string("label", "SDUI"), text))
        action.string("confirmMessage")?.let {
            context.effects.emit(UiEffect.ShowToast(ToastSpec(message = it)))
        }
        return SduiResult.success(JsonPrimitive(text))
    }
}

/** `analytics` — lets a screen emit a custom event alongside the automatic ones. */
public object AnalyticsHandler : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val name = action.string("event") ?: action.string("name")
        if (name.isNullOrBlank()) {
            return SduiResult.failure(SduiError.ActionFailed("analytics", "Missing 'event' parameter"))
        }
        val properties = (action.obj("properties") ?: action.obj("params"))?.toMap().orEmpty()
        context.analytics?.track(
            name,
            properties + mapOf("screen_id" to JsonPrimitive(context.screenId)),
        )
        return SduiResult.success(null)
    }
}

/**
 * `openNativeScreen`, `scanQR`, `camera` and any other host capability.
 *
 * Nothing is dispatched by class name: the JSON supplies a key, and only a
 * capability the host registered can run. An unregistered key fails loudly rather
 * than silently doing nothing, so a mis-typed screen surfaces in QA.
 */
public class NativeCapabilityHandler(private val capabilityKey: String? = null) : ActionHandler {
    override suspend fun handle(action: ResolvedAction, context: ActionContext): SduiResult<JsonElement?> {
        val key = capabilityKey
            ?: action.string("capability")
            ?: action.string("screen")
            ?: action.type
        val capability = context.nativeCapabilities.get(key)
            ?: return SduiResult.failure(
                SduiError.ActionFailed(
                    action.type,
                    "No native capability registered for '$key'. Call " +
                        "Sdui.registerNativeCapability(\"$key\") { … } during initialisation.",
                ),
            )
        return capability.invoke(action.params, context)
    }
}
