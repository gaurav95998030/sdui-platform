package com.sdui.cache

import kotlinx.serialization.Serializable

@Serializable
public data class CacheEntry(
    val key: String,
    val value: String,
    val createdAt: Long,
    val ttlSeconds: Long,
    val etag: String? = null,
) {
    public val ageSeconds: Long get() = (System.currentTimeMillis() - createdAt) / 1000

    /** TTL of 0 means "never expires by time"; only eviction removes it. */
    public val isFresh: Boolean get() = ttlSeconds <= 0 || ageSeconds < ttlSeconds
}

/**
 * Storage seam for screens and API responses.
 *
 * Kept deliberately string-shaped: the cache stores raw payloads, never parsed
 * models, so a schema change can never leave stale typed objects in the store.
 */
public interface SduiCache {
    public suspend fun get(key: String): CacheEntry?
    public suspend fun put(key: String, value: String, ttlSeconds: Long, etag: String? = null)
    public suspend fun remove(key: String)
    public suspend fun clear()

    /** Removes entries whose key starts with [prefix] — used to purge one screen. */
    public suspend fun invalidate(prefix: String)

    public companion object {
        public val Noop: SduiCache = object : SduiCache {
            override suspend fun get(key: String): CacheEntry? = null
            override suspend fun put(key: String, value: String, ttlSeconds: Long, etag: String?) = Unit
            override suspend fun remove(key: String) = Unit
            override suspend fun clear() = Unit
            override suspend fun invalidate(prefix: String) = Unit
        }
    }
}

public object CacheKeys {
    public fun screen(projectId: String, environment: String, screenId: String, version: String?): String =
        "screen:$projectId:$environment:$screenId:${version ?: "published"}"

    public fun api(url: String, method: String, body: String?): String =
        "api:$method:$url:${body?.hashCode() ?: 0}"

    public fun theme(projectId: String, environment: String, themeId: String): String =
        "theme:$projectId:$environment:$themeId"
}
