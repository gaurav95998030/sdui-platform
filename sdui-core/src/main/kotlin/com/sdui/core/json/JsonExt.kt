package com.sdui.core.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Canonical empty object, reused so that default arguments do not allocate. */
public val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

public val EmptyJsonArray: JsonArray = JsonArray(emptyList())

// ---------------------------------------------------------------------------
// Null-safe scalar extraction. Server payloads are untrusted: every accessor
// degrades to `null` rather than throwing, and the caller decides the fallback.
// ---------------------------------------------------------------------------

public fun JsonElement?.asStringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.takeIf { it.isNotEmpty() }
    else -> null
}

public fun JsonElement?.asBooleanOrNull(): Boolean? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> booleanOrNull ?: when (content.lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no", "" -> false
        else -> null
    }
    else -> null
}

public fun JsonElement?.asIntOrNull(): Int? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> intOrNull ?: doubleOrNull?.toInt() ?: content.toIntOrNull()
    else -> null
}

public fun JsonElement?.asLongOrNull(): Long? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> longOrNull ?: doubleOrNull?.toLong() ?: content.toLongOrNull()
    else -> null
}

public fun JsonElement?.asFloatOrNull(): Float? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> doubleOrNull?.toFloat() ?: content.toFloatOrNull()
    else -> null
}

public fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

public fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

/** Truthiness used by `visible`, `if` and other conditional slots. */
public fun JsonElement?.isTruthy(): Boolean = when (this) {
    null, JsonNull -> false
    is JsonPrimitive -> when {
        booleanOrNull != null -> booleanOrNull!!
        doubleOrNull != null -> doubleOrNull != 0.0
        else -> content.isNotEmpty() && !content.equals("false", ignoreCase = true) &&
            content != "0" && !content.equals("null", ignoreCase = true)
    }
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
}

// ---------------------------------------------------------------------------
// Path access — `user.profile.name`, `items[0].label`
// ---------------------------------------------------------------------------

private val INDEX_PART = Regex("""\[(\d+)\]""")
private val INDEX_SUFFIX = Regex("""^(.*?)((?:\[\d+\])+)$""")

/**
 * Resolves a dotted path against a JSON tree. Segments may carry array indices
 * (`items[2]`, `matrix[0][1]`). Returns `null` for any missing or mistyped hop.
 */
public fun JsonElement?.atPath(path: String): JsonElement? {
    if (this == null) return null
    if (path.isBlank()) return this
    var cursor: JsonElement? = this
    for (rawSegment in path.split('.')) {
        if (cursor == null) return null
        val match = INDEX_SUFFIX.matchEntire(rawSegment)
        val key = match?.groupValues?.get(1) ?: rawSegment
        if (key.isNotEmpty()) {
            cursor = (cursor as? JsonObject)?.get(key) ?: return null
        }
        match?.groupValues?.get(2)?.let { indices ->
            for (index in INDEX_PART.findAll(indices)) {
                val i = index.groupValues[1].toIntOrNull() ?: return null
                cursor = (cursor as? JsonArray)?.getOrNull(i) ?: return null
            }
        }
    }
    return cursor?.takeIf { it != JsonNull }
}

/** Immutably writes [value] at a dotted [path], creating intermediate objects. */
public fun JsonObject.withPath(path: String, value: JsonElement): JsonObject {
    val segments = path.split('.').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return value as? JsonObject ?: this
    return writeRecursive(this, segments, 0, value)
}

private fun writeRecursive(
    current: JsonObject,
    segments: List<String>,
    index: Int,
    value: JsonElement,
): JsonObject {
    val key = segments[index]
    val mutated = current.toMutableMap()
    mutated[key] = if (index == segments.lastIndex) {
        value
    } else {
        writeRecursive(current[key] as? JsonObject ?: EmptyJsonObject, segments, index + 1, value)
    }
    return JsonObject(mutated)
}

/** Removes the entry at a dotted [path]. */
public fun JsonObject.withoutPath(path: String): JsonObject {
    val segments = path.split('.').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return this
    if (segments.size == 1) return JsonObject(toMutableMap().apply { remove(segments[0]) })
    val head = segments.first()
    val child = this[head] as? JsonObject ?: return this
    return JsonObject(toMutableMap().apply {
        put(head, child.withoutPath(segments.drop(1).joinToString(".")))
    })
}

/** Deep merge: values from [other] win; nested objects merge recursively. */
public fun JsonObject.deepMerge(other: JsonObject): JsonObject {
    if (other.isEmpty()) return this
    if (isEmpty()) return other
    val merged = toMutableMap()
    for ((key, value) in other) {
        val existing = merged[key]
        merged[key] = if (existing is JsonObject && value is JsonObject) {
            existing.deepMerge(value)
        } else {
            value
        }
    }
    return JsonObject(merged)
}

/** Renders a JSON value the way a UI would print it (strings unquoted). */
public fun JsonElement?.displayString(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}
