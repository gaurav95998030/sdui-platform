package com.sdui.cache

import com.sdui.core.SduiLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** LRU in-memory tier. Fast path for the screen the user just navigated away from. */
public class MemoryCache(private val maxEntries: Int = 32) : SduiCache {

    private val mutex = Mutex()
    private val entries = object : LinkedHashMap<String, CacheEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>): Boolean =
            size > maxEntries
    }

    override suspend fun get(key: String): CacheEntry? = mutex.withLock { entries[key] }

    override suspend fun put(key: String, value: String, ttlSeconds: Long, etag: String?) {
        mutex.withLock {
            entries[key] = CacheEntry(key, value, System.currentTimeMillis(), ttlSeconds, etag)
        }
    }

    override suspend fun remove(key: String) {
        mutex.withLock { entries.remove(key) }
    }

    override suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    override suspend fun invalidate(prefix: String) {
        mutex.withLock { entries.keys.filter { it.startsWith(prefix) }.forEach(entries::remove) }
    }
}

/**
 * File-per-entry disk tier under the app's cache dir.
 *
 * Keys are hashed into filenames so arbitrary URLs are safe on any filesystem, and
 * the whole entry (payload + metadata) is one JSON document — a partially written
 * file simply fails to parse and is treated as a miss.
 */
public class DiskCache(
    private val directory: File,
    private val maxBytes: Long = 16L * 1024 * 1024,
    private val logger: SduiLogger = SduiLogger.Noop,
) : SduiCache {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(CacheEntry.serializer(), file.readText()) }
            .onFailure {
                logger.w("DiskCache", "Corrupt entry for $key; dropping", it)
                file.delete()
            }
            .getOrNull()
    }

    override suspend fun put(key: String, value: String, ttlSeconds: Long, etag: String?) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    directory.mkdirs()
                    val entry = CacheEntry(key, value, System.currentTimeMillis(), ttlSeconds, etag)
                    // Write to a temp file then rename, so readers never see a partial entry.
                    val target = fileFor(key)
                    val temp = File(target.parentFile, target.name + ".tmp")
                    temp.writeText(json.encodeToString(CacheEntry.serializer(), entry))
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    trimIfNeeded()
                }.onFailure { logger.w("DiskCache", "Failed to write $key", it) }
            }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { mutex.withLock { directory.deleteRecursively() } }
    }

    override suspend fun invalidate(prefix: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                directory.listFiles()?.forEach { file ->
                    val entry = runCatching {
                        json.decodeFromString(CacheEntry.serializer(), file.readText())
                    }.getOrNull()
                    if (entry == null || entry.key.startsWith(prefix)) file.delete()
                }
            }
        }
    }

    /** Evicts oldest-first until the directory is back under [maxBytes]. */
    private fun trimIfNeeded() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var index = 0
        while (total > maxBytes && index < files.size) {
            total -= files[index].length()
            files[index].delete()
            index++
        }
    }

    private fun fileFor(key: String): File = File(directory, hash(key))

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(40)
}

/**
 * Memory in front of disk. Reads promote to memory; writes go to both, so a cold
 * start still serves the last known screen instantly from disk.
 */
public class LayeredCache(
    private val memory: SduiCache,
    private val disk: SduiCache,
) : SduiCache {

    override suspend fun get(key: String): CacheEntry? =
        memory.get(key) ?: disk.get(key)?.also {
            memory.put(it.key, it.value, it.ttlSeconds, it.etag)
        }

    override suspend fun put(key: String, value: String, ttlSeconds: Long, etag: String?) {
        memory.put(key, value, ttlSeconds, etag)
        disk.put(key, value, ttlSeconds, etag)
    }

    override suspend fun remove(key: String) {
        memory.remove(key)
        disk.remove(key)
    }

    override suspend fun clear() {
        memory.clear()
        disk.clear()
    }

    override suspend fun invalidate(prefix: String) {
        memory.invalidate(prefix)
        disk.invalidate(prefix)
    }
}
