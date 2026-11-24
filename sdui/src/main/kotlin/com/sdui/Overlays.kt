package com.sdui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.sdui.actions.UiEffect
import com.sdui.core.model.DialogSpec
import com.sdui.core.model.SheetSpec
import com.sdui.core.model.ToastDuration
import com.sdui.renderer.LayoutParent
import com.sdui.renderer.RenderChildren
import com.sdui.renderer.RenderScope

/**
 * Renders the transient UI that actions request.
 *
 * Handlers cannot call composables, so `showDialog`, `bottomSheet` and `showToast`
 * emit a [UiEffect]; this collector is the one place that turns them into UI. It
 * sits above the navigation stack so an overlay survives a screen swap underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayEffects(engine: SduiEngine) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHost = remember { SnackbarHostState() }

    var dialog by remember { mutableStateOf<DialogSpec?>(null) }
    var sheet by remember { mutableStateOf<SheetSpec?>(null) }

    LaunchedEffect(engine) {
        engine.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> {
                    val spec = effect.spec
                    if (spec.snackbar) {
                        val result = snackbarHost.showSnackbar(
                            message = spec.message,
                            actionLabel = spec.actionLabel,
                            duration = if (spec.duration == ToastDuration.LONG) {
                                androidx.compose.material3.SnackbarDuration.Long
                            } else {
                                androidx.compose.material3.SnackbarDuration.Short
                            },
                        )
                        if (result == SnackbarResult.ActionPerformed && spec.action != null) {
                            engine.navigator.current?.let { entry ->
                                engine.dispatcher.dispatch(
                                    spec.action!!,
                                    engine.actionContext(entry.entryId, entry.screenId, emptyMap(), emptyMap()) {},
                                )
                            }
                        }
                    } else {
                        Toast.makeText(
                            context,
                            spec.message,
                            if (spec.duration == ToastDuration.LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                is UiEffect.ShowDialog -> dialog = effect.spec
                is UiEffect.ShowSheet -> sheet = effect.spec
                UiEffect.DismissDialog -> dialog = null
                UiEffect.DismissSheet -> sheet = null
                UiEffect.HideKeyboard -> keyboard?.hide()
                is UiEffect.ScrollTo -> Unit // handled by the list components
            }
        }
    }

    dialog?.let { spec ->
        AlertDialog(
            onDismissRequest = { if (spec.dismissible) dialog = null },
            title = spec.title?.let { { Text(it) } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    spec.message?.let { Text(it) }
                    spec.content?.let { node ->
                        RenderChildren(listOf(node), RenderScope("dialog"), LayoutParent.Column(this))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    spec.confirmAction?.let { dispatchFromOverlay(engine, it) }
                }) {
                    Text(
                        text = spec.confirmLabel ?: "OK",
                        color = if (spec.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            androidx.compose.ui.graphics.Color.Unspecified
                        },
                    )
                }
            },
            dismissButton = spec.cancelLabel?.let { label ->
                {
                    TextButton(onClick = {
                        dialog = null
                        spec.cancelAction?.let { dispatchFromOverlay(engine, it) }
                    }) { Text(label) }
                }
            },
        )
    }

    sheet?.let { spec ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = spec.fullHeight),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                spec.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                spec.content?.let { node ->
                    RenderChildren(listOf(node), RenderScope("sheet"), LayoutParent.Column(this))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
        SnackbarHost(snackbarHost)
    }
}

private fun dispatchFromOverlay(engine: SduiEngine, action: com.sdui.core.model.ActionSpec) {
    val entry = engine.navigator.current ?: return
    engine.dispatcher.dispatchAsync(
        action,
        engine.actionContext(entry.entryId, entry.screenId, emptyMap(), emptyMap()) {},
    )
}
