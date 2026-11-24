package com.sdui.navigation

import com.sdui.core.json.EmptyJsonObject
import com.sdui.core.model.NavigationMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicLong

/** How an entry is presented on top of the stack. */
public enum class Presentation { SCREEN, MODAL, BOTTOM_SHEET }

/**
 * One live destination. [entryId] is unique per push, so navigating to the same
 * screen twice yields two independent state scopes.
 */
public data class NavEntry(
    val entryId: String,
    val screenId: String,
    val args: JsonObject = EmptyJsonObject,
    val presentation: Presentation = Presentation.SCREEN,
    /** Value handed back by a child that was popped with a result. */
    val pendingResult: JsonObject? = null,
)

public data class NavigationRequest(
    val screenId: String? = null,
    val mode: NavigationMode = NavigationMode.PUSH,
    val args: JsonObject = EmptyJsonObject,
    /** For [NavigationMode.POP_TO]; pops until this screen is on top. */
    val target: String? = null,
    val result: JsonObject? = null,
    val clearStack: Boolean = false,
)

public sealed interface NavEvent {
    public data class Pushed(val entry: NavEntry, val from: NavEntry?) : NavEvent
    public data class Popped(val entry: NavEntry, val to: NavEntry?) : NavEvent
    public data class Replaced(val entry: NavEntry, val previous: NavEntry?) : NavEvent
    public data object ExitRequested : NavEvent
}

/**
 * The whole navigation graph, decided at runtime.
 *
 * There is no `NavHost`, no route table and no compile-time destination list —
 * the server names a screen id and this manager pushes it. That is what allows a
 * dashboard-authored screen to become reachable without an app release.
 *
 * Modals and bottom sheets live on the same stack, tagged by [Presentation], so
 * back handling, analytics and state lifetime work identically for all three.
 */
public class NavigationManager {

    private val counter = AtomicLong(0)

    private val _backStack = MutableStateFlow<List<NavEntry>>(emptyList())
    public val backStack: StateFlow<List<NavEntry>> = _backStack.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    public val events: Flow<NavEvent> = _events.asSharedFlow()

    public val current: NavEntry? get() = _backStack.value.lastOrNull()

    /** Topmost full-screen entry; what renders behind a modal or sheet. */
    public val currentScreen: NavEntry?
        get() = _backStack.value.lastOrNull { it.presentation == Presentation.SCREEN }

    public val canGoBack: Boolean get() = _backStack.value.size > 1

    public val depth: Int get() = _backStack.value.size

    /** Installs the first destination, discarding any existing stack. */
    public fun setRoot(screenId: String, args: JsonObject = EmptyJsonObject) {
        val entry = newEntry(screenId, args, Presentation.SCREEN)
        _backStack.value = listOf(entry)
        _events.tryEmit(NavEvent.Pushed(entry, null))
    }

    public fun navigate(request: NavigationRequest): Boolean = when (request.mode) {
        NavigationMode.PUSH -> push(request, Presentation.SCREEN)
        NavigationMode.MODAL -> push(request, Presentation.MODAL)
        NavigationMode.BOTTOM_SHEET -> push(request, Presentation.BOTTOM_SHEET)
        NavigationMode.REPLACE -> replace(request)
        NavigationMode.POP -> pop(request.result)
        NavigationMode.POP_TO_ROOT -> popToRoot(request.result)
        NavigationMode.POP_TO -> popTo(request.target ?: request.screenId, request.result)
    }

    private fun push(request: NavigationRequest, presentation: Presentation): Boolean {
        val screenId = request.screenId ?: return false
        val from = current
        val entry = newEntry(screenId, request.args, presentation)
        _backStack.value = (if (request.clearStack) emptyList() else _backStack.value) + entry
        _events.tryEmit(NavEvent.Pushed(entry, from))
        return true
    }

    private fun replace(request: NavigationRequest): Boolean {
        val screenId = request.screenId ?: return false
        val previous = current
        val entry = newEntry(screenId, request.args, previous?.presentation ?: Presentation.SCREEN)
        _backStack.value = _backStack.value.dropLast(1) + entry
        _events.tryEmit(NavEvent.Replaced(entry, previous))
        return true
    }

    /**
     * @return false when the stack has a single entry, letting the host decide
     * whether that means "finish the activity" or "ignore".
     */
    public fun pop(result: JsonObject? = null): Boolean {
        val stack = _backStack.value
        val top = stack.lastOrNull() ?: return false
        if (stack.size <= 1) {
            _events.tryEmit(NavEvent.ExitRequested)
            return false
        }
        val remaining = stack.dropLast(1)
        _backStack.value = attachResult(remaining, result)
        _events.tryEmit(NavEvent.Popped(top, remaining.lastOrNull()))
        return true
    }

    public fun popToRoot(result: JsonObject? = null): Boolean {
        val stack = _backStack.value
        if (stack.size <= 1) return false
        val top = stack.last()
        val remaining = listOf(stack.first())
        _backStack.value = attachResult(remaining, result)
        _events.tryEmit(NavEvent.Popped(top, remaining.last()))
        return true
    }

    public fun popTo(screenId: String?, result: JsonObject? = null): Boolean {
        if (screenId == null) return false
        val stack = _backStack.value
        val index = stack.indexOfLast { it.screenId == screenId }
        if (index < 0 || index == stack.lastIndex) return false
        val top = stack.last()
        val remaining = stack.subList(0, index + 1).toList()
        _backStack.value = attachResult(remaining, result)
        _events.tryEmit(NavEvent.Popped(top, remaining.last()))
        return true
    }

    /** Dismisses the topmost modal/bottom sheet, leaving full screens untouched. */
    public fun dismissOverlay(result: JsonObject? = null): Boolean {
        val stack = _backStack.value
        if (stack.lastOrNull()?.presentation == Presentation.SCREEN) return false
        return pop(result)
    }

    /** Clears the result once the destination screen has consumed it. */
    public fun consumeResult(entryId: String) {
        _backStack.value = _backStack.value.map {
            if (it.entryId == entryId) it.copy(pendingResult = null) else it
        }
    }

    public fun clear() {
        _backStack.value = emptyList()
    }

    private fun attachResult(stack: List<NavEntry>, result: JsonObject?): List<NavEntry> {
        if (result == null || stack.isEmpty()) return stack
        return stack.dropLast(1) + stack.last().copy(pendingResult = result)
    }

    private fun newEntry(screenId: String, args: JsonObject, presentation: Presentation) = NavEntry(
        entryId = "$screenId#${counter.incrementAndGet()}",
        screenId = screenId,
        args = args,
        presentation = presentation,
    )
}
