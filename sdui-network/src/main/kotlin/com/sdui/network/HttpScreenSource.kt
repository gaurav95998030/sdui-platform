package com.sdui.network

import com.sdui.cache.CacheKeys
import com.sdui.cache.SduiCache
import com.sdui.core.LogLevel
import com.sdui.core.SduiConfig
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.contract.ScreenRequest
import com.sdui.core.contract.ScreenSource
import com.sdui.core.contract.ScreenUpdate
import com.sdui.core.contract.ThemeSource
import com.sdui.core.json.SduiJson
import com.sdui.core.model.ScreenEnvelope
import com.sdui.core.model.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads published screens from the SDUI backend.
 *
 * Caching is deliberately handled here rather than by OkHttp's HTTP cache: the
 * SDK needs *offline-first* semantics (serve a stale screen indefinitely when
 * there is no connectivity) which HTTP freshness rules cannot express.
 */
public class HttpScreenSource(
    private val config: SduiConfig,
    private val cache: SduiCache = SduiCache.Noop,
    private val client: OkHttpClient = OkHttpClientFactory.create(config),
    private val liveUpdates: Flow<ScreenUpdate>? = null,
) : ScreenSource {

    override suspend fun load(request: ScreenRequest): SduiResult<ScreenEnvelope> {
        val key = CacheKeys.screen(
            config.projectId, config.environment.slug, request.screenId, request.version,
        )

        if (!request.forceRefresh && config.cache.enabled) {
            cache.get(key)?.let { entry ->
                if (entry.isFresh) {
                    return decode(entry.value, fromCache = true)
                }
            }
        }

        val fetched = fetch(request, key)
        if (fetched is SduiResult.Failure && config.cache.enabled && config.cache.serveStaleWhenOffline) {
            cache.get(key)?.let { entry ->
                config.logger.log(
                    LogLevel.INFO, "ScreenSource",
                    "Serving stale '${request.screenId}' (${entry.ageSeconds}s old): ${fetched.error.message}",
                )
                return decode(entry.value, fromCache = true)
            }
        }
        return fetched
    }

    override fun updates(): Flow<ScreenUpdate> = liveUpdates ?: kotlinx.coroutines.flow.emptyFlow()

    private suspend fun fetch(request: ScreenRequest, cacheKey: String): SduiResult<ScreenEnvelope> =
        withContext(Dispatchers.IO) {
            SduiResult.catching {
                val base = "${config.screensEndpoint}/${request.screenId}".toHttpUrlOrNull()
                    ?: throw SduiError.Parse("Invalid baseUrl '${config.baseUrl}'")

                val url = base.newBuilder().apply {
                    addQueryParameter("env", config.environment.slug)
                    request.version?.let { addQueryParameter("version", it) }
                    request.params.forEach { (k, v) -> addQueryParameter(k, v) }
                    // Targeting inputs for feature flags, rollouts and experiments.
                    config.user.userId?.let { addQueryParameter("userId", it) }
                    config.user.country?.let { addQueryParameter("country", it) }
                    config.user.appVersion?.let { addQueryParameter("appVersion", it) }
                    if (config.user.isPremium) addQueryParameter("premium", "true")
                }.build()

                val httpRequest = Request.Builder()
                    .url(url)
                    .apply {
                        config.defaultHeaders.forEach { (k, v) -> header(k, v) }
                        config.apiKey?.let { header("X-Api-Key", it) }
                        header("Accept", "application/json")
                    }
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.code == 404 -> throw SduiError.ScreenNotFound(request.screenId)
                        !response.isSuccessful ->
                            throw SduiError.Http(response.code, text, "HTTP ${response.code} loading '${request.screenId}'")
                    }

                    val envelope = SduiJson.decodeFromString(ScreenEnvelope.serializer(), text)
                    if (config.cache.enabled) {
                        val ttl = envelope.ttlSeconds.takeIf { it > 0 } ?: config.cache.screenTtlSeconds
                        cache.put(cacheKey, text, ttl, envelope.etag)
                    }
                    envelope
                }
            }
        }

    private fun decode(raw: String, fromCache: Boolean): SduiResult<ScreenEnvelope> =
        SduiResult.catching { SduiJson.decodeFromString(ScreenEnvelope.serializer(), raw) }
            .let { result ->
                when (result) {
                    is SduiResult.Success -> SduiResult.success(result.value, fromCache)
                    is SduiResult.Failure -> result
                }
            }
}

/** Loads the design system from the backend, cached under its own key space. */
public class HttpThemeSource(
    private val config: SduiConfig,
    private val cache: SduiCache = SduiCache.Noop,
    private val client: OkHttpClient = OkHttpClientFactory.create(config),
) : ThemeSource {

    override suspend fun load(themeId: String): SduiResult<ThemeSpec> = withContext(Dispatchers.IO) {
        val key = CacheKeys.theme(config.projectId, config.environment.slug, themeId)
        if (config.cache.enabled) {
            cache.get(key)?.takeIf { it.isFresh }?.let { entry ->
                return@withContext SduiResult.catching {
                    SduiJson.decodeFromString(ThemeSpec.serializer(), entry.value)
                }
            }
        }
        SduiResult.catching {
            val url = "${config.baseUrl.trimEnd('/')}/api/v1/projects/${config.projectId}/themes/$themeId" +
                "?env=${config.environment.slug}"
            val request = Request.Builder().url(url)
                .apply { config.apiKey?.let { header("X-Api-Key", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SduiError.Http(response.code, text, "HTTP ${response.code} loading theme '$themeId'")
                }
                if (config.cache.enabled) cache.put(key, text, config.cache.screenTtlSeconds)
                SduiJson.decodeFromString(ThemeSpec.serializer(), text)
            }
        }
    }
}
