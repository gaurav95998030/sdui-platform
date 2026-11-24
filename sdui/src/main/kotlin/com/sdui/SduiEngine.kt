package com.sdui

import android.content.Context
import com.sdui.actions.ActionContext
import com.sdui.actions.ActionDispatcher
import com.sdui.actions.ActionRegistry
import com.sdui.actions.DefaultActions
import com.sdui.actions.FormRegistry
import com.sdui.actions.NativeCapabilityRegistry
import com.sdui.actions.UiEffect
import com.sdui.actions.UiEffectSink
import com.sdui.analytics.AnalyticsDispatcher
import com.sdui.cache.DiskCache
import com.sdui.cache.LayeredCache
import com.sdui.cache.MemoryCache
import com.sdui.cache.SduiCache
import com.sdui.components.DefaultComponents
import com.sdui.core.SduiConfig
import com.sdui.core.SduiLogger
import com.sdui.core.onFailure
import com.sdui.core.onSuccess
import com.sdui.core.contract.ScreenSource
import com.sdui.core.contract.ThemeSource
import com.sdui.core.model.ThemeSpec
import com.sdui.navigation.NavigationManager
import com.sdui.network.ApiEngine
import com.sdui.network.CompositeScreenSource
import com.sdui.network.HttpScreenSource
import com.sdui.network.HttpThemeSource
import com.sdui.network.LivePreviewClient
import com.sdui.network.OkHttpClientFactory
import com.sdui.parser.ScreenParser
import com.sdui.renderer.ComponentRegistry
import com.sdui.state.StateManager
import com.sdui.state.expression.ExpressionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * The composition root: one instance owns every registry, store and client for
 * the lifetime of an SDUI session.
 *
 * Constructed by [Sdui.initialize] rather than a DI graph so the SDK works
 * identically in a Hilt app, a Koin app, or an app with no DI at all. Hosts that
 * *do* use Hilt simply `@Provides` this instance — see the demo app's
 * `SduiModule`.
 */
public class SduiEngine internal constructor(
    public val config: SduiConfig,
    internal val appContext: Context,
    public val components: ComponentRegistry,
    public val actions: ActionRegistry,
    public val nativeCapabilities: NativeCapabilityRegistry,
    public val expressions: ExpressionEngine,
    public val screenSource: ScreenSource,
    public val themeSource: ThemeSource?,
    public val cache: SduiCache,
    public val apiEngine: ApiEngine,
    public val analytics: AnalyticsDispatcher,
    public val parser: ScreenParser,
    public val navigator: NavigationManager,
    public val stateManager: StateManager,
    public val forms: FormRegistry,
    public val logger: SduiLogger,
) {
    public val dispatcher: ActionDispatcher = ActionDispatcher(actions)

    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _theme = MutableStateFlow(config.fallbackTheme)
    public val theme: StateFlow<ThemeSpec> = _theme.asStateFlow()

    private val _darkOverride = MutableStateFlow<Boolean?>(null)
    public val darkOverride: StateFlow<Boolean?> = _darkOverride.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    public val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    internal val effectSink: UiEffectSink = UiEffectSink { effect -> _effects.tryEmit(effect) }

    /** Live-preview stream; null unless the host enabled it. */
    internal val liveUpdates = if (config.livePreview.enabled) {
        LivePreviewClient(config).updates()
    } else {
        null
    }

    public fun setTheme(spec: ThemeSpec) {
        _theme.value = spec
    }

    public fun setDarkMode(dark: Boolean?) {
        _darkOverride.value = dark
    }

    /** Loads a theme by id from the backend and applies it. */
    public suspend fun applyTheme(themeId: String) {
        val source = themeSource ?: return
        source.load(themeId).onSuccess { setTheme(it) }
            .onFailure { logger.w("Theme", "Could not load theme '$themeId': ${it.message}") }
    }

    /**
     * Builds the dispatch context for a screen. Everything an action can touch is
     * threaded through here, which keeps handlers free of globals.
     */
    internal fun actionContext(
        entryId: String,
        screenId: String,
        apis: Map<String, com.sdui.core.model.ApiSpec>,
        locals: Map<String, JsonElement>,
        onRefresh: suspend (Boolean) -> Unit,
    ): ActionContext = ActionContext(
        androidContext = appContext,
        entryId = entryId,
        screenId = screenId,
        screenState = stateManager.storeFor(entryId),
        stateManager = stateManager,
        navigator = navigator,
        effects = effectSink,
        expressions = expressions,
        dispatcher = dispatcher,
        nativeCapabilities = nativeCapabilities,
        forms = forms,
        logger = logger,
        coroutineScope = scope,
        apis = apis,
        apiEngine = apiEngine,
        analytics = analytics,
        locals = locals,
        refreshScreen = onRefresh,
        applyTheme = { applyTheme(it) },
    )

    public fun shutdown() {
        scope.cancel()
        stateManager.releaseAll()
        navigator.clear()
    }

    internal companion object {
        /** Values exposed to bindings as `env.*`. */
        fun environmentFor(config: SduiConfig): JsonObject = JsonObject(
            buildMap<String, JsonElement> {
                put("environment", JsonPrimitive(config.environment.slug))
                put("projectId", JsonPrimitive(config.projectId))
                put("apiBase", JsonPrimitive(config.baseUrl.trimEnd('/')))
                put("platform", JsonPrimitive("android"))
                config.user.userId?.let { put("userId", JsonPrimitive(it)) }
                config.user.appVersion?.let { put("appVersion", JsonPrimitive(it)) }
                config.user.country?.let { put("country", JsonPrimitive(it)) }
                config.user.locale?.let { put("locale", JsonPrimitive(it)) }
                put("isPremium", JsonPrimitive(config.user.isPremium))
                config.user.attributes.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
            },
        )

        fun build(context: Context, config: SduiConfig, customization: SduiBuilder.() -> Unit): SduiEngine {
            val appContext = context.applicationContext
            val builder = SduiBuilder(appContext, config).apply(customization)
            return builder.build()
        }
    }
}

/**
 * Assembles an [SduiEngine], applying host customisation before anything is wired.
 *
 * Registration happens here — not after start-up — so the first screen render
 * already sees every custom component and action, avoiding a frame where a
 * host component is "unknown".
 */
public class SduiBuilder internal constructor(
    private val appContext: Context,
    private val config: SduiConfig,
) {
    public val components: ComponentRegistry = DefaultComponents.createRegistry()
    public val actions: ActionRegistry = DefaultActions.createRegistry()
    public val nativeCapabilities: NativeCapabilityRegistry = NativeCapabilityRegistry()
    public val expressions: ExpressionEngine = ExpressionEngine()
    public val analytics: AnalyticsDispatcher =
        AnalyticsDispatcher(enabled = config.analyticsEnabled, logger = config.logger)

    /** Replaces the default HTTP screen source (bundled assets, mocks, a CMS…). */
    public var screenSource: ScreenSource? = null

    /** Consulted after the primary source fails — typically bundled assets. */
    public var fallbackScreenSource: ScreenSource? = null

    public var themeSource: ThemeSource? = null
    public var cache: SduiCache? = null
    public var initialGlobalState: JsonObject = com.sdui.core.json.EmptyJsonObject

    internal fun build(): SduiEngine {
        val logger = config.logger

        val resolvedCache = cache ?: if (config.cache.enabled) {
            LayeredCache(
                memory = MemoryCache(config.cache.memoryEntries),
                disk = DiskCache(
                    directory = File(appContext.cacheDir, "sdui"),
                    maxBytes = config.cache.diskBytes,
                    logger = logger,
                ),
            )
        } else {
            SduiCache.Noop
        }

        val httpClient = OkHttpClientFactory.create(config)
        val primary = screenSource ?: HttpScreenSource(config, resolvedCache, httpClient)
        val composed = fallbackScreenSource
            ?.let { CompositeScreenSource(listOf(primary, it)) }
            ?: primary

        return SduiEngine(
            config = config,
            appContext = appContext,
            components = components,
            actions = actions,
            nativeCapabilities = nativeCapabilities,
            expressions = expressions,
            screenSource = composed,
            themeSource = themeSource ?: HttpThemeSource(config, resolvedCache, httpClient),
            cache = resolvedCache,
            apiEngine = ApiEngine(config, resolvedCache, httpClient),
            analytics = analytics,
            parser = ScreenParser(),
            navigator = NavigationManager(),
            stateManager = StateManager(
                globalInitial = initialGlobalState,
                environment = SduiEngine.environmentFor(config),
            ),
            forms = FormRegistry(),
            logger = logger,
        )
    }
}
