package com.sdui.actions

import android.content.Context
import com.sdui.analytics.AnalyticsDispatcher
import com.sdui.core.SduiLogger
import com.sdui.core.SduiResult
import com.sdui.core.model.ApiSpec
import com.sdui.core.model.DialogSpec
import com.sdui.core.model.SheetSpec
import com.sdui.core.model.ToastSpec
import com.sdui.core.registry.Registry
import com.sdui.navigation.NavigationManager
import com.sdui.network.ApiEngine
import com.sdui.state.BindingScope
import com.sdui.state.StateManager
import com.sdui.state.StateStore
import com.sdui.state.expression.ExpressionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Something an action wants the UI layer to show. Actions run off the composition,
 * so anything requiring a composable — dialogs, sheets, snackbars — is emitted as
 * an effect and rendered by the host.
 */
public sealed interface UiEffect {
    public data class ShowToast(val spec: ToastSpec) : UiEffect
    public data class ShowDialog(val spec: DialogSpec) : UiEffect
    public data class ShowSheet(val spec: SheetSpec) : UiEffect
    public data object DismissDialog : UiEffect
    public data object DismissSheet : UiEffect
    public data object HideKeyboard : UiEffect
    public data class ScrollTo(val nodeId: String) : UiEffect
}

public fun interface UiEffectSink {
    public fun emit(effect: UiEffect)
}

/**
 * A native capability exposed to server-authored screens.
 *
 * This is the security boundary the platform depends on: JSON can *request*
 * `scanQR`, but only a capability the host explicitly registered can run, and the
 * host decides permissions, consent and the shape of the returned data. Nothing
 * here is resolved by reflection or class name, so a payload cannot reach code
 * the app did not deliberately expose.
 */
public fun interface NativeCapability {
    public suspend fun invoke(params: JsonObject, context: ActionContext): SduiResult<JsonElement?>
}

public class NativeCapabilityRegistry : Registry<NativeCapability>("NativeCapabilityRegistry")

/**
 * Everything a handler needs to do its job. Constructed per dispatch by the
 * renderer, and cheap to derive with [withLocals] for nested chains.
 */
public data class ActionContext(
    val androidContext: Context,
    val entryId: String,
    val screenId: String,
    val screenState: StateStore,
    val stateManager: StateManager,
    val navigator: NavigationManager,
    val effects: UiEffectSink,
    val expressions: ExpressionEngine,
    /** Lets a handler run nested actions (`then`, dialog confirm, API callbacks). */
    val dispatcher: ActionDispatcher,
    val nativeCapabilities: NativeCapabilityRegistry,
    val forms: FormRegistry,
    val logger: SduiLogger,
    val coroutineScope: CoroutineScope,
    val apis: Map<String, ApiSpec> = emptyMap(),
    val apiEngine: ApiEngine? = null,
    val analytics: AnalyticsDispatcher? = null,
    /** `item`/`index` from an enclosing `repeat`, plus chain outputs like `response`. */
    val locals: Map<String, JsonElement> = emptyMap(),
    /** Re-fetches the current screen definition. */
    val refreshScreen: suspend (force: Boolean) -> Unit = {},
    /** Switches the active theme by id. */
    val applyTheme: suspend (themeId: String) -> Unit = {},
) {
    val globalState: StateStore get() = stateManager.global

    /** Rebuilt on each read so a chain sees writes made by the previous step. */
    public fun bindingScope(): BindingScope =
        stateManager.scopeFor(screenState.snapshot).withLocals(locals)

    public fun withLocals(extra: Map<String, JsonElement>): ActionContext =
        if (extra.isEmpty()) this else copy(locals = locals + extra)
}
