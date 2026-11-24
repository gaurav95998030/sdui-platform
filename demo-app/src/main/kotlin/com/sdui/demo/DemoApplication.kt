package com.sdui.demo

import android.app.Application
import com.sdui.Sdui
import com.sdui.actions.ActionHandler
import com.sdui.actions.NativeCapability
import com.sdui.analytics.AnalyticsSink
import com.sdui.core.SduiConfig
import com.sdui.core.SduiLogger
import com.sdui.core.SduiEnvironment
import com.sdui.core.SduiResult
import com.sdui.core.UserContext
import com.sdui.demo.components.RatingRenderer
import com.sdui.network.AssetScreenSource
import com.sdui.network.HttpAnalyticsSink
import dagger.hilt.android.HiltAndroidApp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Shows the complete host-side integration: one `initialize` call, a handful of
 * registrations, and nothing else. Every screen, action and navigation edge in
 * this app comes from JSON.
 */
@HiltAndroidApp
class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Sdui.initialize(
            context = this,
            config = SduiConfig(
                // 10.0.2.2 is the host machine as seen from the emulator, where the
                // dashboard backend runs during development.
                baseUrl = BACKEND_URL,
                projectId = "demo",
                environment = SduiEnvironment.DEV,
                logger = SduiLogger.Android,
                strictMode = BuildConfigCompat.DEBUG,
                livePreview = com.sdui.core.LivePreviewConfig(enabled = true),
                network = com.sdui.core.NetworkConfig(enableHttpLogging = true),
                user = UserContext(
                    userId = "u_1024",
                    isPremium = true,
                    country = "IN",
                    appVersion = "1.0.0",
                ),
                fallbackTheme = DemoThemes.Brand,
            ),
        ) {
            // Bundled JSON keeps the app fully functional before the backend is up.
            fallbackScreenSource = AssetScreenSource(this@DemoApplication)

            initialGlobalState = buildJsonObject {
                put(
                    "user",
                    buildJsonObject {
                        put("name", "Ada Lovelace")
                        put("firstName", "Ada")
                        put("email", "ada@example.com")
                        put("isPremium", true)
                        put("avatar", "https://i.pravatar.cc/160?img=47")
                    },
                )
                put(
                    "wallet",
                    buildJsonObject {
                        put("balance", 42150.75)
                        put("currency", "INR")
                    },
                )
            }
        }

        // --- Extension points: a custom component, action and native capability ---

        Sdui.registerComponent("rating", RatingRenderer)

        Sdui.registerAction(
            type = "demoPing",
            handler = ActionHandler { action, context ->
                context.logger.i("Demo", "demoPing ${action.params}")
                SduiResult.success(JsonPrimitive("pong"))
            },
        )

        // The SDK ships the `scanQR` vocabulary; the app owns the implementation,
        // its permissions and the shape of the result.
        Sdui.registerNativeCapability(
            key = "scanQR",
            capability = NativeCapability { _, _ ->
                // A real app launches its scanner here and returns the decoded value.
                SduiResult.success(JsonPrimitive("SDUI-DEMO-4821"))
            },
        )

        Sdui.registerFunction("maskCard") { args ->
            val raw = (args.firstOrNull() as? JsonPrimitive)?.content.orEmpty()
            JsonPrimitive(if (raw.length < 4) raw else "•••• " + raw.takeLast(4))
        }

        Sdui.addAnalyticsSink(
            AnalyticsSink { event ->
                android.util.Log.i("DemoAnalytics", "${event.name} ${event.properties}")
            },
        )

        // Ships the SDK's automatic instrumentation to the backend, which is what
        // fills the dashboard's Analytics panel. A production app would add its own
        // sink here too (Firebase, Amplitude, …) — sinks compose.
        Sdui.addAnalyticsSink(
            HttpAnalyticsSink(config = requireNotNull(Sdui.engine).config),
        )
    }

    private companion object {
        const val BACKEND_URL = "http://10.0.2.2:8787"
    }
}

/** Avoids depending on generated BuildConfig, which this module disables. */
internal object BuildConfigCompat {
    val DEBUG: Boolean = true
}
