package com.sdui.core

import com.sdui.core.model.ThemeSpec

public enum class SduiEnvironment(public val slug: String) {
    DEV("dev"), QA("qa"), STAGE("stage"), PRODUCTION("prod");

    public companion object {
        public fun fromSlug(slug: String): SduiEnvironment =
            entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) } ?: PRODUCTION
    }
}

/**
 * Everything the host tells the SDK at start-up. Immutable by design: changing
 * environments means re-initialising, which keeps caches and in-flight requests
 * from straddling two configurations.
 */
public data class SduiConfig(
    val baseUrl: String,
    val projectId: String,
    val environment: SduiEnvironment = SduiEnvironment.PRODUCTION,

    val apiKey: String? = null,
    val defaultHeaders: Map<String, String> = emptyMap(),

    /**
     * Resolves the named credential for [com.sdui.core.model.AuthSpec]. Tokens are
     * fetched per request so rotation and refresh stay entirely with the host.
     */
    val authTokenProvider: AuthTokenProvider = AuthTokenProvider { null },

    val logger: SduiLogger = SduiLogger.Noop,
    val minLogLevel: LogLevel = LogLevel.WARN,

    val network: NetworkConfig = NetworkConfig(),
    val cache: CacheConfig = CacheConfig(),
    val livePreview: LivePreviewConfig = LivePreviewConfig(),
    val analyticsEnabled: Boolean = true,

    val user: UserContext = UserContext(),

    /** Theme used before a screen declares one, and when the server sends none. */
    val fallbackTheme: ThemeSpec = ThemeSpec.Default,

    /**
     * When true, an unknown component type or action throws instead of degrading
     * to a placeholder. Intended for debug builds and dashboard preview only.
     */
    val strictMode: Boolean = false,
) {
    public val screensEndpoint: String
        get() = baseUrl.trimEnd('/') + "/api/v1/projects/$projectId/screens"
}

public fun interface AuthTokenProvider {
    public suspend fun token(credential: String): String?
}

public data class NetworkConfig(
    val connectTimeoutMs: Long = 15_000,
    val readTimeoutMs: Long = 20_000,
    val writeTimeoutMs: Long = 20_000,
    val defaultRetryAttempts: Int = 2,
    val enableHttpLogging: Boolean = false,
)

public data class CacheConfig(
    val enabled: Boolean = true,
    val screenTtlSeconds: Long = 60 * 60,
    val memoryEntries: Int = 32,
    val diskBytes: Long = 16L * 1024 * 1024,
    /** Serve cached screens when the device is offline, ignoring TTL. */
    val serveStaleWhenOffline: Boolean = true,
    /** Fetch a fresh copy in the background after serving from cache. */
    val backgroundRefresh: Boolean = true,
)

public data class LivePreviewConfig(
    val enabled: Boolean = false,
    /** SSE endpoint; defaults to `<baseUrl>/api/v1/preview/stream`. */
    val streamUrl: String? = null,
    val reconnectDelayMs: Long = 3_000,
    /** Pairs a device with a dashboard session so only that device hot-reloads. */
    val deviceToken: String? = null,
)

/** Targeting inputs for feature flags, rollouts and experiments. */
public data class UserContext(
    val userId: String? = null,
    val isPremium: Boolean = false,
    val country: String? = null,
    val locale: String? = null,
    val appVersion: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
