package com.sdui.network

import com.sdui.cache.CacheKeys
import com.sdui.cache.SduiCache
import com.sdui.core.LogLevel
import com.sdui.core.SduiConfig
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.json.SduiJson
import com.sdui.core.model.ApiSpec
import com.sdui.core.model.AuthType
import com.sdui.core.model.CachePolicy
import com.sdui.core.model.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

public data class ApiResponse(
    val status: Int,
    val body: JsonElement?,
    val rawBody: String,
    val headers: Map<String, String> = emptyMap(),
    val fromCache: Boolean = false,
    val stale: Boolean = false,
) {
    public val isSuccess: Boolean get() = status in 200..299
}

/**
 * Executes the network calls a screen declares.
 *
 * The engine deals only in *already-resolved* specs — the action layer has
 * substituted every `{{binding}}` before calling in. That keeps this class free
 * of state and expression concerns, and makes it trivially testable.
 *
 * Responsibilities: method/headers/query/body assembly, authentication, timeouts,
 * retry with exponential backoff, response parsing, and the five cache policies.
 */
public class ApiEngine(
    private val config: SduiConfig,
    private val cache: SduiCache = SduiCache.Noop,
    client: OkHttpClient? = null,
    private val interceptors: List<SduiInterceptor> = emptyList(),
    private val backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val client: OkHttpClient = client ?: OkHttpClientFactory.create(config)

    public suspend fun execute(spec: ApiSpec): SduiResult<ApiResponse> {
        val cacheKey = spec.cache.key
            ?: CacheKeys.api(spec.url, spec.method.name, spec.body?.toString())

        // Cache-first policies answer before touching the network.
        if (spec.cache.enabled) {
            val entry = cache.get(cacheKey)
            when (spec.cache.policy) {
                CachePolicy.CACHE_ONLY ->
                    return entry?.let { SduiResult.success(it.toResponse(stale = !it.isFresh), fromCache = true) }
                        ?: SduiResult.failure(SduiError.Network("Nothing cached for ${spec.url}"))

                CachePolicy.CACHE_FIRST ->
                    if (entry != null && entry.isFresh) {
                        return SduiResult.success(entry.toResponse(), fromCache = true)
                    }

                CachePolicy.STALE_WHILE_REVALIDATE ->
                    if (entry != null) {
                        // Serve immediately, refresh in the background.
                        backgroundScope.launch { performWithRetry(spec, cacheKey) }
                        return SduiResult.success(entry.toResponse(stale = !entry.isFresh), fromCache = true)
                    }

                else -> Unit
            }
        }

        val result = performWithRetry(spec, cacheKey)

        // Network-first falls back to whatever is cached when the call fails.
        if (result is SduiResult.Failure && spec.cache.enabled &&
            spec.cache.policy != CachePolicy.NETWORK_ONLY
        ) {
            cache.get(cacheKey)?.let { entry ->
                config.logger.log(
                    LogLevel.INFO, "ApiEngine",
                    "Serving cached ${spec.url} after ${result.error.code}",
                )
                return SduiResult.success(entry.toResponse(stale = true), fromCache = true)
            }
        }
        return result
    }

    private suspend fun performWithRetry(spec: ApiSpec, cacheKey: String): SduiResult<ApiResponse> {
        val attempts = spec.retry.maxAttempts.coerceAtLeast(1)
        var delayMs = spec.retry.backoffMs
        var last: SduiResult<ApiResponse> = SduiResult.failure(SduiError.Unknown("No attempt made"))

        repeat(attempts) { attempt ->
            last = perform(spec, cacheKey)
            val error = last.errorOrNull() ?: return last

            val retryable = when (error) {
                is SduiError.Http -> error.status in spec.retry.retryOnStatus
                is SduiError.Network, is SduiError.Timeout -> spec.retry.retryOnNetworkError
                else -> false
            }
            if (!retryable || attempt == attempts - 1) return last

            config.logger.log(
                LogLevel.DEBUG, "ApiEngine",
                "Retrying ${spec.url} in ${delayMs}ms (attempt ${attempt + 2}/$attempts)",
            )
            delay(delayMs)
            delayMs = (delayMs * spec.retry.multiplier).toLong().coerceAtMost(30_000)
        }
        return last
    }

    private suspend fun perform(spec: ApiSpec, cacheKey: String): SduiResult<ApiResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(spec)
                val response = client.newCall(request).await()
                response.use { raw ->
                    val bodyText = raw.body?.string().orEmpty()
                    if (!raw.isSuccessful) {
                        val error = if (raw.code == 401 || raw.code == 403) {
                            SduiError.Unauthorized("${raw.code} for ${spec.url}")
                        } else {
                            SduiError.Http(raw.code, bodyText, "HTTP ${raw.code} for ${spec.url}")
                        }
                        return@withContext SduiResult.failure(error)
                    }

                    val parsed = parseBody(bodyText)
                    if (spec.cache.enabled && spec.method == HttpMethod.GET) {
                        cache.put(cacheKey, bodyText, spec.cache.ttlSeconds, raw.header("ETag"))
                    }
                    SduiResult.success(
                        ApiResponse(
                            status = raw.code,
                            body = parsed,
                            rawBody = bodyText,
                            headers = raw.headers.toMap(),
                        ),
                    )
                }
            } catch (io: IOException) {
                SduiResult.failure(SduiError.from(io))
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                SduiResult.failure(SduiError.from(t))
            }
        }

    private suspend fun buildRequest(spec: ApiSpec): Request {
        val base = spec.url.toHttpUrlOrNull()
            ?: throw SduiError.Parse("Malformed URL '${spec.url}'")

        val urlBuilder = base.newBuilder()
        spec.query.forEach { (key, value) -> if (value.isNotEmpty()) urlBuilder.addQueryParameter(key, value) }

        val builder = Request.Builder().url(urlBuilder.build())

        config.defaultHeaders.forEach { (k, v) -> builder.header(k, v) }
        config.apiKey?.let { builder.header("X-Api-Key", it) }
        builder.header("X-Sdui-Environment", config.environment.slug)
        spec.headers.forEach { (k, v) -> builder.header(k, v) }

        spec.auth?.let { auth ->
            when (auth.type) {
                AuthType.NONE -> Unit
                AuthType.BEARER -> config.authTokenProvider.token(auth.credential)
                    ?.let { builder.header(auth.headerName, "${auth.scheme} $it") }

                AuthType.BASIC -> config.authTokenProvider.token(auth.credential)
                    ?.let { builder.header(auth.headerName, "Basic $it") }

                AuthType.HEADER, AuthType.CUSTOM -> config.authTokenProvider.token(auth.credential)
                    ?.let { builder.header(auth.headerName, it) }

                AuthType.QUERY -> config.authTokenProvider.token(auth.credential)?.let {
                    builder.url(urlBuilder.addQueryParameter(auth.headerName, it).build())
                }
            }
        }

        val body = if (spec.method.allowsBody) {
            val payload = spec.body?.takeUnless { it is JsonNull }?.toString() ?: "{}"
            payload.toRequestBody(JSON_MEDIA_TYPE)
        } else {
            null
        }

        builder.method(spec.method.name, body)
        var request = builder.build()
        for (interceptor in interceptors) request = interceptor.intercept(request)
        return request
    }

    private fun parseBody(text: String): JsonElement? {
        if (text.isBlank()) return null
        return runCatching { SduiJson.parseToJsonElement(text) }
            .getOrElse { JsonPrimitive(text) }
    }

    private fun com.sdui.cache.CacheEntry.toResponse(stale: Boolean = false) = ApiResponse(
        status = 200,
        body = runCatching { SduiJson.parseToJsonElement(value) }.getOrNull(),
        rawBody = value,
        fromCache = true,
        stale = stale,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Request-level hook. Hosts add tracing headers, signing, or device metadata. */
public fun interface SduiInterceptor {
    public fun intercept(request: Request): Request
}

private fun okhttp3.Headers.toMap(): Map<String, String> =
    (0 until size).associate { name(it) to value(it) }

/** Bridges OkHttp's callback API to coroutines with proper cancellation. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
