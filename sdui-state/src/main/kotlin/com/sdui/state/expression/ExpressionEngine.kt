package com.sdui.state.expression

import com.sdui.core.json.isTruthy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Collections

/**
 * Compiles and evaluates `{{ … }}` bindings.
 *
 * Compilation results are cached by source text, so the parser runs once per
 * unique expression for the lifetime of the process even though evaluation
 * happens on every state change.
 *
 * ```
 * {{ user.name }}                          -> "Ada"
 * {{ wallet.balance > 0 }}                 -> true
 * {{ currency(wallet.balance, "INR") }}    -> "₹1,240.00"
 * Hello {{ user.firstName }}, you have {{ cart.items.length }} items
 * ```
 */
public class ExpressionEngine(
    public val functions: ExpressionFunctions = ExpressionFunctions.builtIns(),
    cacheSize: Int = 512,
) {
    private val cache: MutableMap<String, Any> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Any>(cacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>): Boolean =
                size > cacheSize
        },
    )

    /** Compiles [source], returning null (and caching the failure) if it is invalid. */
    public fun compile(source: String): Expr? {
        val cached = cache[source]
        if (cached != null) return cached as? Expr
        return try {
            ExprParser(source).parse().also { cache[source] = it }
        } catch (e: ExpressionSyntaxException) {
            cache[source] = INVALID
            null
        }
    }

    public fun evaluate(source: String, scope: ValueScope): JsonElement? {
        val expr = compile(source) ?: return null
        return Evaluator(scope, functions).evaluate(expr)
    }

    public fun evaluateBoolean(source: String, scope: ValueScope, default: Boolean = false): Boolean {
        if (source.isBlank()) return default
        val expression = unwrap(source)
        return evaluate(expression, scope).isTruthy()
    }

    /**
     * Resolves a template that may mix literal text and `{{ }}` fragments.
     *
     * When the template is *exactly* one binding the raw [JsonElement] is returned
     * so arrays and objects survive; otherwise the result is a concatenated string.
     */
    public fun interpolate(template: String, scope: ValueScope): JsonElement? {
        if (!template.contains(OPEN)) return JsonPrimitive(template)

        val single = SINGLE_BINDING.matchEntire(template.trim())
        if (single != null) return evaluate(single.groupValues[1].trim(), scope)

        val builder = StringBuilder(template.length)
        var cursor = 0
        while (true) {
            val open = template.indexOf(OPEN, cursor)
            if (open < 0) {
                builder.append(template, cursor, template.length)
                break
            }
            // "\{{" escapes a literal brace pair.
            if (open > 0 && template[open - 1] == '\\') {
                builder.append(template, cursor, open - 1).append(OPEN)
                cursor = open + OPEN.length
                continue
            }
            val close = template.indexOf(CLOSE, open)
            if (close < 0) {
                builder.append(template, cursor, template.length)
                break
            }
            builder.append(template, cursor, open)
            builder.append(evaluate(template.substring(open + OPEN.length, close).trim(), scope).asText())
            cursor = close + CLOSE.length
        }
        return JsonPrimitive(builder.toString())
    }

    /** Deep-resolves every string in a props object, leaving non-string values intact. */
    public fun resolveTree(element: JsonElement, scope: ValueScope): JsonElement = when (element) {
        is JsonPrimitive ->
            if (element.isString && element.content.contains(OPEN)) {
                interpolate(element.content, scope) ?: JsonNull
            } else {
                element
            }

        is JsonArray -> JsonArray(element.map { resolveTree(it, scope) })
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> resolveTree(value, scope) })
    }

    /** True when [text] contains at least one binding. */
    public fun isDynamic(text: String): Boolean = text.contains(OPEN)

    /** Strips the outer `{{ }}` if present, so callers can accept either form. */
    public fun unwrap(source: String): String {
        val trimmed = source.trim()
        val match = SINGLE_BINDING.matchEntire(trimmed) ?: return trimmed
        return match.groupValues[1].trim()
    }

    public fun clearCache(): Unit = cache.clear()

    private companion object {
        const val OPEN = "{{"
        const val CLOSE = "}}"
        // Braces are escaped on both sides: Android's ICU regex engine rejects an
        // unescaped closing brace, unlike the JVM implementation.
        val SINGLE_BINDING = Regex("""^\{\{([^{}]*)\}\}$""", RegexOption.DOT_MATCHES_ALL)
        val INVALID = Any()
    }
}
