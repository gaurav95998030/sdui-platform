package com.sdui.core.registry

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, string-keyed plugin table — the single extension mechanism used by
 * components, actions and native bridges.
 *
 * Lookup is a hash-map hit, never reflection and never a `when` over known types,
 * so adding a component or action is a one-line registration by the host app.
 */
public open class Registry<T : Any>(
    private val name: String,
    /** Consulted when a key is unknown; lets callers supply a placeholder. */
    private val fallback: ((String) -> T?)? = null,
) {
    private val entries = ConcurrentHashMap<String, T>()
    private val aliases = ConcurrentHashMap<String, String>()

    public val keys: Set<String> get() = entries.keys.toSet()

    public val size: Int get() = entries.size

    /**
     * @param override when false, a duplicate key throws instead of silently
     * shadowing an existing plugin — misregistration is a programming error.
     */
    public fun register(key: String, value: T, override: Boolean = false) {
        val normalized = normalize(key)
        if (!override && entries.containsKey(normalized)) {
            throw IllegalStateException(
                "$name already has an entry for '$key'. Pass override = true to replace it.",
            )
        }
        entries[normalized] = value
    }

    public fun registerAll(values: Map<String, T>, override: Boolean = false) {
        values.forEach { (key, value) -> register(key, value, override) }
    }

    /** Makes [alias] resolve to whatever [target] resolves to (e.g. `vstack` -> `column`). */
    public fun alias(alias: String, target: String) {
        aliases[normalize(alias)] = normalize(target)
    }

    public fun unregister(key: String): T? = entries.remove(normalize(key))

    public fun get(key: String): T? {
        val normalized = normalize(key)
        return entries[normalized]
            ?: aliases[normalized]?.let { entries[it] }
            ?: fallback?.invoke(key)
    }

    public fun require(key: String): T =
        get(key) ?: throw NoSuchElementException("$name has no entry for '$key'")

    public fun contains(key: String): Boolean = get(key) != null

    public fun clear() {
        entries.clear()
        aliases.clear()
    }

    /** Keys are matched case-insensitively so `"Text"` and `"text"` are the same type. */
    private fun normalize(key: String): String = key.trim().lowercase()
}
