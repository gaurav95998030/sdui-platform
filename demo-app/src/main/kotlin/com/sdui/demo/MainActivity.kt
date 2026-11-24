package com.sdui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.sdui.Sdui
import com.sdui.SduiHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * The entire UI layer of this app.
 *
 * There is no navigation graph, no screen-specific composable and no view model:
 * the activity names one screen and hands the rest to the SDK.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LaunchedEffect(Unit) { Sdui.loadScreen("home") }
            SduiHost(onExit = { finish() })
        }
    }
}
