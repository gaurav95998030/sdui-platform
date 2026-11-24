package com.sdui.core.contract

import com.sdui.core.SduiResult
import com.sdui.core.model.ScreenEnvelope
import com.sdui.core.model.ThemeSpec
import kotlinx.coroutines.flow.Flow

/**
 * Where screen definitions come from. The default implementation talks to the
 * SDUI backend, but a host can swap in a bundled-assets source for offline-first
 * apps or a mock source for tests — the renderer never knows the difference.
 */
public interface ScreenSource {
    public suspend fun load(request: ScreenRequest): SduiResult<ScreenEnvelope>

    /** Emits whenever a screen is republished, driving live preview. */
    public fun updates(): Flow<ScreenUpdate> = kotlinx.coroutines.flow.emptyFlow()
}

public data class ScreenRequest(
    val screenId: String,
    val params: Map<String, String> = emptyMap(),
    /** Bypasses cache when true. */
    val forceRefresh: Boolean = false,
    /** Requests a specific version instead of the published one (dashboard preview). */
    val version: String? = null,
)

public data class ScreenUpdate(
    val screenId: String,
    val version: String?,
    val envelope: ScreenEnvelope? = null,
)

/** Supplies the design system; separate from [ScreenSource] so themes cache independently. */
public interface ThemeSource {
    public suspend fun load(themeId: String): SduiResult<ThemeSpec>
    public fun updates(): Flow<ThemeSpec> = kotlinx.coroutines.flow.emptyFlow()
}

/** Gate for server-controlled rollouts. Consulted before a screen renders. */
public interface FeatureFlagSource {
    public suspend fun isEnabled(key: String, default: Boolean = false): Boolean
    public suspend fun variant(experimentId: String): String?
    public fun snapshot(): Map<String, Boolean>
}
