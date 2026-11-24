package com.sdui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sdui.components.MaterialIcons
import com.sdui.core.UiState
import com.sdui.core.model.AppBarSpec
import com.sdui.core.model.SduiScreen
import com.sdui.navigation.NavEntry
import com.sdui.navigation.Presentation
import com.sdui.renderer.LayoutParent
import com.sdui.renderer.LocalBindingScope
import com.sdui.renderer.LocalComponentRegistry
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderChildren
import com.sdui.renderer.RenderScope
import com.sdui.renderer.boundText
import com.sdui.renderer.ScreenRuntime
import com.sdui.state.BindingScope
import com.sdui.theme.LocalSduiTheme
import com.sdui.theme.SduiTheme

/**
 * The single composable a host app needs.
 *
 * It renders whatever is on the navigation stack: the topmost full screen, any
 * modals and bottom sheets above it, and the overlay effects that actions emit.
 * Because the stack is driven entirely by server-described `navigate` actions,
 * adding a destination never touches this function.
 *
 * ```kotlin
 * setContent {
 *     LaunchedEffect(Unit) { Sdui.loadScreen("home") }
 *     SduiHost(onExit = { finish() })
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SduiHost(
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
) {
    val engine = Sdui.requireEngine()
    val stack by engine.navigator.backStack.collectAsState()
    val theme by engine.theme.collectAsState()
    val darkOverride by engine.darkOverride.collectAsState()

    SduiTheme(spec = theme, darkOverride = darkOverride) {
        Box(modifier.fillMaxSize()) {
            stack.lastOrNull { it.presentation == Presentation.SCREEN }?.let { base ->
                key(base.entryId) { ScreenContainer(entry = base, engine = engine) }
            }

            // Overlay entries render above the base screen, oldest first.
            stack.filter { it.presentation != Presentation.SCREEN }.forEach { overlay ->
                key(overlay.entryId) {
                    when (overlay.presentation) {
                        Presentation.MODAL -> Dialog(onDismissRequest = { engine.navigator.pop() }) {
                            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                                ScreenContainer(overlay, engine)
                            }
                        }

                        Presentation.BOTTOM_SHEET -> ModalBottomSheet(
                            onDismissRequest = { engine.navigator.pop() },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        ) { ScreenContainer(overlay, engine) }

                        Presentation.SCREEN -> Unit
                    }
                }
            }

            OverlayEffects(engine)
        }

        BackHandler {
            val top = stack.lastOrNull()
            val screen = top?.let { ControllerCache.peek(it.entryId)?.state?.value?.dataOrNull }
            when {
                // A screen may intercept back — e.g. to confirm an unsaved form.
                top != null && screen != null && screen.onBack.isNotEmpty() ->
                    engine.dispatcher.dispatchAsync(
                        screen.onBack,
                        engine.actionContext(top.entryId, top.screenId, screen.apis, emptyMap()) {},
                    )

                !engine.navigator.pop() -> onExit()
            }
        }
    }
}

/**
 * Renders one navigation entry: loading, error and content states, the optional
 * server-described app bar, and the component tree itself.
 */
@Composable
internal fun ScreenContainer(entry: NavEntry, engine: SduiEngine) {
    val controller = remember(entry.entryId) {
        ScreenController(engine, entry, engine.scope).also { ControllerCache.put(entry.entryId, it) }
    }
    DisposableEffect(entry.entryId) {
        onDispose {
            ControllerCache.remove(entry.entryId)
            controller.dispose()
        }
    }

    val uiState by controller.state.collectAsState()
    val screenState by controller.store.state.collectAsState()
    val globalState by engine.stateManager.global.state.collectAsState()

    val binding = remember(screenState, globalState) {
        BindingScope(
            screen = screenState,
            global = globalState,
            env = SduiEngine.environmentFor(engine.config),
        )
    }

    val apis = uiState.dataOrNull?.apis ?: emptyMap()
    val runtime = remember(entry.entryId, apis) {
        ScreenRuntime(
            entryId = entry.entryId,
            screenId = entry.screenId,
            screenState = controller.store,
            expressions = engine.expressions,
            dispatcher = engine.dispatcher,
            forms = engine.forms,
            analytics = engine.analytics,
            logger = engine.logger,
            strictMode = engine.config.strictMode,
            actionContext = { locals ->
                engine.actionContext(entry.entryId, entry.screenId, apis, locals) { force ->
                    controller.load(force)
                }
            },
        )
    }

    CompositionLocalProvider(
        LocalScreenRuntime provides runtime,
        LocalComponentRegistry provides engine.components,
        LocalBindingScope provides binding,
    ) {
        when (val current = uiState) {
            is UiState.Success -> ScreenScaffold(current.data, entry, engine)

            // A refresh keeps the previous tree on screen instead of flashing a spinner.
            is UiState.Loading -> current.previous
                ?.let { ScreenScaffold(it, entry, engine) }
                ?: LoadingScreen()

            is UiState.Error -> current.previous
                ?.let { ScreenScaffold(it, entry, engine) }
                ?: ErrorScreen(current.error.message) { controller.refresh() }

            UiState.Idle -> LoadingScreen()
        }
    }
}

@Composable
private fun ScreenScaffold(screen: SduiScreen, entry: NavEntry, engine: SduiEngine) {
    val theme = LocalSduiTheme.current

    LaunchedEffect(entry.entryId, screen.id) {
        if (screen.onAppear.isNotEmpty()) {
            engine.dispatcher.dispatchAsync(
                screen.onAppear,
                engine.actionContext(entry.entryId, entry.screenId, screen.apis, emptyMap()) {},
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = theme.color(screen.background) ?: MaterialTheme.colorScheme.background,
        topBar = { screen.appBar?.let { ScreenAppBar(it, engine) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .let { if (screen.scrollable) it.verticalScroll(rememberScrollState()) else it },
            verticalArrangement = Arrangement.Top,
        ) {
            RenderChildren(listOf(screen.root), RenderScope(path = "root"), LayoutParent.Column(this))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenAppBar(spec: AppBarSpec, engine: SduiEngine) {
    val theme = LocalSduiTheme.current
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = theme.color(spec.background) ?: MaterialTheme.colorScheme.surface,
        titleContentColor = theme.color(spec.contentColor) ?: MaterialTheme.colorScheme.onSurface,
    )
    val title: @Composable () -> Unit = {
        Column {
            Text(boundText(spec.title).orEmpty(), style = MaterialTheme.typography.titleLarge)
            boundText(spec.subtitle)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    val navigation: @Composable () -> Unit = {
        if (spec.showBack && engine.navigator.canGoBack) {
            IconButton(onClick = { engine.navigator.pop() }) {
                MaterialIcons.resolve("arrowBack")?.let { Icon(it, contentDescription = "Back") }
            }
        }
    }

    if (spec.centered) {
        CenterAlignedTopAppBar(title = title, navigationIcon = navigation, colors = colors)
    } else {
        TopAppBar(title = title, navigationIcon = navigation, colors = colors)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * Lets the back handler reach a live controller without threading it through
 * composition.
 */
internal object ControllerCache {
    private val controllers = java.util.concurrent.ConcurrentHashMap<String, ScreenController>()

    fun put(entryId: String, controller: ScreenController) {
        controllers[entryId] = controller
    }

    fun peek(entryId: String): ScreenController? = controllers[entryId]

    fun remove(entryId: String) {
        controllers.remove(entryId)
    }
}
