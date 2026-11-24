package com.sdui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdui.navigation.NavEvent
import com.sdui.navigation.NavigationManager
import com.sdui.state.StateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/**
 * Shows how native app code coexists with server-driven UI: the session lives in
 * the SDK's global store, so a plain MVVM view model can write to it and every
 * screen's `{{user.*}}` bindings update, without either side knowing about the
 * other's screens.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val globalState: StateStore,
    private val navigator: NavigationManager,
) : ViewModel() {

    val userName: StateFlow<String> = globalState.observe("user.name")
        .map { (it as? JsonPrimitive)?.content ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch {
            navigator.events.collect { event ->
                if (event is NavEvent.Pushed) {
                    globalState.set("lastScreen", event.entry.screenId)
                }
            }
        }
    }

    fun signOut() {
        globalState.reset()
        navigator.setRoot("home")
    }
}
