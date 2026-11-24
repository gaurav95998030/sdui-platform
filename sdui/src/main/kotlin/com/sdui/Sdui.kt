package com.sdui

import android.content.Context
import com.sdui.actions.ActionHandler
import com.sdui.actions.NativeCapability
import com.sdui.analytics.AnalyticsSink
import com.sdui.core.SduiConfig
import com.sdui.core.json.EmptyJsonObject
import com.sdui.core.model.NavigationMode
import com.sdui.core.model.ThemeSpec
import com.sdui.navigation.NavigationRequest
import com.sdui.renderer.ComponentRenderer
import com.sdui.state.StateStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The SDK's public surface.
 *
 * Two calls cover the intended usage:
 *
 * ```kotlin
 * Sdui.initialize(context, SduiConfig(baseUrl = …, projectId = …))
 * Sdui.loadScreen("home")
 * ```
 *
 * Everything else on this object exists for *extension* — registering the host's
 * own components, actions and native capabilities. All internal machinery
 * (renderer, dispatcher, stores, parser, cache) is reached through
 * [SduiEngine] and is not needed for normal use.
 */
public object Sdui {

    @Volatile
    private var engineRef: SduiEngine? = null

    public val isInitialized: Boolean get() = engineRef != null

    /** The live engine, or null before [initialize]. */
    public val engine: SduiEngine? get() = engineRef

    internal fun requireEngine(): SduiEngine = engineRef ?: error(
        "SDUI is not initialized. Call Sdui.initialize(context, config) from Application.onCreate().",
    )

    /**
     * Builds the engine. Safe to call once per process; calling it again replaces
     * the previous engine and discards its state, which is what a user switching
     * environments or logging out actually wants.
     *
     * @param customize registers host components/actions *before* the first render,
     * so no frame ever shows an "unknown component".
     */
    public fun initialize(
        context: Context,
        config: SduiConfig,
        customize: SduiBuilder.() -> Unit = {},
    ): SduiEngine {
        synchronized(this) {
            engineRef?.shutdown()
            val built = SduiEngine.build(context, config, customize)
            engineRef = built
            built.logger.i(
                "Sdui",
                "Initialized project '${config.projectId}' on ${config.environment.slug} " +
                    "with ${built.components.size} components and ${built.actions.size} actions",
            )
            return built
        }
    }

    /** Installs an engine built elsewhere — the seam Hilt/Koin modules use. */
    public fun install(engine: SduiEngine) {
        synchronized(this) {
            engineRef?.shutdown()
            engineRef = engine
        }
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    /**
     * Makes [screenId] the root of the stack and renders it.
     *
     * This is the *only* screen the app names in code; every subsequent
     * destination arrives through a server-described `navigate` action.
     */
    public fun loadScreen(screenId: String, args: JsonObject = EmptyJsonObject) {
        requireEngine().navigator.setRoot(screenId, args)
    }

    /** Pushes a screen imperatively; rarely needed, since JSON drives navigation. */
    public fun navigate(
        screenId: String,
        mode: NavigationMode = NavigationMode.PUSH,
        args: JsonObject = EmptyJsonObject,
    ) {
        requireEngine().navigator.navigate(NavigationRequest(screenId, mode, args))
    }

    public fun back(): Boolean = requireEngine().navigator.pop()

    // -----------------------------------------------------------------------
    // Extension points
    // -----------------------------------------------------------------------

    /**
     * Adds a component type. The renderer resolves it by name at render time, so a
     * screen published a minute later can use it immediately.
     *
     * ```kotlin
     * Sdui.registerComponent("rating", RatingRenderer())
     * ```
     */
    public fun registerComponent(
        type: String,
        renderer: ComponentRenderer,
        override: Boolean = false,
    ) {
        requireEngine().components.register(type, renderer, override)
    }

    /** Adds an action type — `"biometricAuth"`, `"addToCalendar"`, … */
    public fun registerAction(type: String, handler: ActionHandler, override: Boolean = false) {
        requireEngine().actions.register(type, handler, override)
    }

    /**
     * Grants server-authored screens access to a device capability.
     *
     * This is the security boundary: JSON can request `scanQR`, but only the
     * implementation registered here runs, and it owns permissions and consent.
     *
     * ```kotlin
     * Sdui.registerNativeCapability("scanQR") { params, _ ->
     *     SduiResult.success(JsonPrimitive(scanner.scan()))
     * }
     * ```
     */
    public fun registerNativeCapability(
        key: String,
        capability: NativeCapability,
        override: Boolean = false,
    ) {
        requireEngine().nativeCapabilities.register(key, capability, override)
    }

    /** Adds a formatting function usable inside `{{ }}`. */
    public fun registerFunction(name: String, function: (List<JsonElement?>) -> JsonElement?) {
        requireEngine().expressions.functions.register(name, function)
    }

    public fun addAnalyticsSink(sink: AnalyticsSink) {
        requireEngine().analytics.addSink(sink)
    }

    // -----------------------------------------------------------------------
    // State & theme
    // -----------------------------------------------------------------------

    /** Session-wide state, readable from every screen as `{{user.name}}`. */
    public val globalState: StateStore get() = requireEngine().stateManager.global

    public fun setTheme(theme: ThemeSpec) {
        requireEngine().setTheme(theme)
    }

    /** Forces light/dark, or `null` to follow the system. */
    public fun setDarkMode(dark: Boolean?) {
        requireEngine().setDarkMode(dark)
    }

    /** Drops cached screens and API responses; the next load hits the network. */
    public suspend fun clearCache() {
        requireEngine().cache.clear()
    }

    public fun shutdown() {
        synchronized(this) {
            engineRef?.shutdown()
            engineRef = null
        }
    }
}
