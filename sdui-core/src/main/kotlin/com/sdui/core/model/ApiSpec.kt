package com.sdui.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A server-described network call. Screens declare these once under
 * `"apis"` and reference them by key from actions, which keeps endpoints out of
 * individual button definitions.
 *
 * ```json
 * "apis": {
 *   "expenses": {
 *     "url": "{{env.apiBase}}/expenses",
 *     "method": "GET",
 *     "query": { "month": "{{filters.month}}" },
 *     "stateKey": "expenses",
 *     "loadingKey": "ui.expensesLoading",
 *     "cache": { "policy": "staleWhileRevalidate", "ttlSeconds": 300 },
 *     "retry": { "maxAttempts": 3 }
 *   }
 * }
 * ```
 */
@Serializable
public data class ApiSpec(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val auth: AuthSpec? = null,
    val timeoutMs: Long? = null,
    val retry: RetrySpec = RetrySpec.None,
    val cache: CacheSpec = CacheSpec.Disabled,

    /** State path the (optionally transformed) response is written to. */
    val stateKey: String? = null,

    /** Dotted path selecting a sub-tree of the response before it is stored. */
    val responsePath: String? = null,

    /** State path toggled `true` while the call is in flight. */
    val loadingKey: String? = null,

    /** State path receiving the error message on failure. */
    val errorKey: String? = null,

    val onSuccess: List<ActionSpec> = emptyList(),
    val onError: List<ActionSpec> = emptyList(),

    /** Extra pagination metadata; see `PaginationSpec`. */
    val pagination: PaginationSpec? = null,
)

@Serializable
public data class RetrySpec(
    val maxAttempts: Int = 1,
    val backoffMs: Long = 500,
    val multiplier: Float = 2f,
    val retryOnStatus: List<Int> = listOf(408, 425, 429, 500, 502, 503, 504),
    val retryOnNetworkError: Boolean = true,
) {
    public companion object {
        public val None: RetrySpec = RetrySpec(maxAttempts = 1)
    }
}

@Serializable
public enum class CachePolicy {
    @SerialName("networkOnly") NETWORK_ONLY,
    @SerialName("cacheFirst") CACHE_FIRST,
    @SerialName("networkFirst") NETWORK_FIRST,
    @SerialName("cacheOnly") CACHE_ONLY,
    @SerialName("staleWhileRevalidate") STALE_WHILE_REVALIDATE,
}

@Serializable
public data class CacheSpec(
    val policy: CachePolicy = CachePolicy.NETWORK_ONLY,
    val ttlSeconds: Long = 0,
    val key: String? = null,
) {
    public val enabled: Boolean get() = policy != CachePolicy.NETWORK_ONLY

    public companion object {
        public val Disabled: CacheSpec = CacheSpec()
    }
}

@Serializable
public enum class AuthType {
    @SerialName("none") NONE,
    @SerialName("bearer") BEARER,
    @SerialName("basic") BASIC,
    @SerialName("header") HEADER,
    @SerialName("query") QUERY,
    @SerialName("custom") CUSTOM,
}

/**
 * Describes *how* to authenticate, never the credential itself. The actual token
 * is supplied at runtime by the host through `SduiConfig.authTokenProvider`,
 * so secrets never travel inside screen JSON.
 */
@Serializable
public data class AuthSpec(
    val type: AuthType = AuthType.BEARER,
    val headerName: String = "Authorization",
    val scheme: String = "Bearer",
    /** Selects which named credential to use when the host manages several. */
    val credential: String = "default",
)

@Serializable
public data class PaginationSpec(
    /** Query parameter carrying the page/cursor value. */
    val pageParam: String = "page",
    val sizeParam: String = "size",
    val pageSize: Int = 20,
    val startPage: Int = 1,
    /** Response path holding the list of items to append. */
    val itemsPath: String = "items",
    /** Response path holding the next cursor; when absent, page numbers are used. */
    val nextCursorPath: String? = null,
    val hasMorePath: String? = null,
)
