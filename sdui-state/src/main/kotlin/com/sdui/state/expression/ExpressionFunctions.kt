package com.sdui.state.expression

import com.sdui.core.json.atPath
import com.sdui.core.json.isTruthy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The function vocabulary available inside `{{ }}`.
 *
 * Extending it is the escape hatch for formatting that would otherwise force a
 * new app release — a host registers `sdui.expressions.register("maskCard") { … }`
 * and every screen can call it immediately.
 */
public class ExpressionFunctions private constructor(
    private val functions: MutableMap<String, (List<JsonElement?>) -> JsonElement?>,
) {
    public fun register(name: String, function: (List<JsonElement?>) -> JsonElement?) {
        functions[name.lowercase()] = function
    }

    public fun has(name: String): Boolean = functions.containsKey(name.lowercase())

    public fun invoke(name: String, args: List<JsonElement?>): JsonElement? =
        functions[name.lowercase()]?.invoke(args)

    public fun copy(): ExpressionFunctions = ExpressionFunctions(LinkedHashMap(functions))

    public companion object {
        public val Default: ExpressionFunctions get() = builtIns()

        public fun builtIns(): ExpressionFunctions {
            val map = LinkedHashMap<String, (List<JsonElement?>) -> JsonElement?>()
            val fns = ExpressionFunctions(map)

            fun arg(args: List<JsonElement?>, i: Int): JsonElement? = args.getOrNull(i)

            // --- text -----------------------------------------------------------
            fns.register("upper") { JsonPrimitive(arg(it, 0).asText().uppercase()) }
            fns.register("lower") { JsonPrimitive(arg(it, 0).asText().lowercase()) }
            fns.register("trim") { JsonPrimitive(arg(it, 0).asText().trim()) }
            fns.register("capitalize") {
                JsonPrimitive(
                    arg(it, 0).asText().replaceFirstChar { c -> c.titlecase(Locale.getDefault()) },
                )
            }
            fns.register("concat") { args -> JsonPrimitive(args.joinToString("") { it.asText() }) }
            fns.register("substring") { args ->
                val text = arg(args, 0).asText()
                val start = arg(args, 1).asNumber()?.toInt()?.coerceIn(0, text.length) ?: 0
                val end = arg(args, 2).asNumber()?.toInt()?.coerceIn(start, text.length) ?: text.length
                JsonPrimitive(text.substring(start, end))
            }
            fns.register("replace") { args ->
                JsonPrimitive(arg(args, 0).asText().replace(arg(args, 1).asText(), arg(args, 2).asText()))
            }
            fns.register("split") { args ->
                JsonArray(arg(args, 0).asText().split(arg(args, 1).asText()).map { JsonPrimitive(it) })
            }
            fns.register("truncate") { args ->
                val text = arg(args, 0).asText()
                val max = arg(args, 1).asNumber()?.toInt() ?: 40
                JsonPrimitive(if (text.length <= max) text else text.take(max).trimEnd() + "…")
            }
            fns.register("initials") { args ->
                JsonPrimitive(
                    arg(args, 0).asText().split(' ', '.').filter { it.isNotBlank() }
                        .take(2).joinToString("") { it.first().uppercase() },
                )
            }
            fns.register("padstart") { args ->
                val text = arg(args, 0).asText()
                val len = arg(args, 1).asNumber()?.toInt() ?: text.length
                val pad = arg(args, 2).asText().ifEmpty { "0" }.first()
                JsonPrimitive(text.padStart(len, pad))
            }

            // --- numbers --------------------------------------------------------
            fns.register("number") { JsonPrimitive(arg(it, 0).asNumber() ?: 0.0) }
            fns.register("round") { args ->
                val value = arg(args, 0).asNumber() ?: return@register JsonNull
                val digits = arg(args, 1).asNumber()?.toInt() ?: 0
                val factor = Math.pow(10.0, digits.toDouble())
                JsonPrimitive((value * factor).roundToLong() / factor)
            }
            fns.register("abs") { args -> arg(args, 0).asNumber()?.let { JsonPrimitive(abs(it)) } }
            fns.register("min") { args ->
                args.mapNotNull { it.asNumber() }.minOrNull()?.let { JsonPrimitive(it) }
            }
            fns.register("max") { args ->
                args.mapNotNull { it.asNumber() }.maxOrNull()?.let { JsonPrimitive(it) }
            }
            fns.register("sum") { args ->
                val list = arg(args, 0) as? JsonArray ?: return@register JsonPrimitive(0)
                val path = arg(args, 1).asText()
                JsonPrimitive(
                    list.sumOf { element ->
                        if (path.isEmpty()) element.asNumber() ?: 0.0
                        else element.atPath(path).asNumber() ?: 0.0
                    },
                )
            }
            fns.register("percent") { args ->
                val value = arg(args, 0).asNumber() ?: 0.0
                val total = arg(args, 1).asNumber() ?: 0.0
                JsonPrimitive(if (total == 0.0) 0.0 else (value / total * 100.0))
            }
            fns.register("currency") { args ->
                val value = arg(args, 0).asNumber() ?: 0.0
                val code = arg(args, 1).asText().ifEmpty { "USD" }
                val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
                runCatching { formatter.currency = Currency.getInstance(code) }
                JsonPrimitive(formatter.format(value))
            }
            fns.register("compact") { args ->
                val value = arg(args, 0).asNumber() ?: 0.0
                val abs = abs(value)
                JsonPrimitive(
                    when {
                        abs >= 1_000_000_000 -> "${trimZeros(value / 1_000_000_000)}B"
                        abs >= 1_000_000 -> "${trimZeros(value / 1_000_000)}M"
                        abs >= 1_000 -> "${trimZeros(value / 1_000)}K"
                        else -> trimZeros(value)
                    },
                )
            }

            // --- collections ----------------------------------------------------
            fns.register("length") { args ->
                JsonPrimitive(
                    when (val v = arg(args, 0)) {
                        is JsonArray -> v.size
                        is JsonObject -> v.size
                        else -> v.asText().length
                    },
                )
            }
            fns.register("join") { args ->
                val list = arg(args, 0) as? JsonArray ?: return@register JsonPrimitive("")
                JsonPrimitive(list.joinToString(arg(args, 1).asText().ifEmpty { ", " }) { it.asText() })
            }
            fns.register("take") { args ->
                val list = arg(args, 0) as? JsonArray ?: return@register JsonArray(emptyList())
                JsonArray(list.take(arg(args, 1).asNumber()?.toInt() ?: list.size))
            }
            fns.register("contains") { args ->
                JsonPrimitive(
                    when (val v = arg(args, 0)) {
                        is JsonArray -> v.any { it.asText() == arg(args, 1).asText() }
                        is JsonObject -> v.containsKey(arg(args, 1).asText())
                        else -> v.asText().contains(arg(args, 1).asText())
                    },
                )
            }
            fns.register("isempty") { JsonPrimitive(!arg(it, 0).isTruthy()) }

            // --- logic / fallbacks ----------------------------------------------
            fns.register("default") { args -> args.firstOrNull { it.isTruthy() } ?: arg(args, 1) }
            fns.register("coalesce") { args -> args.firstOrNull { it != null && it !is JsonNull } }
            fns.register("if") { args ->
                if (arg(args, 0).isTruthy()) arg(args, 1) else arg(args, 2)
            }
            fns.register("not") { JsonPrimitive(!arg(it, 0).isTruthy()) }
            fns.register("bool") { JsonPrimitive(arg(it, 0).isTruthy()) }
            fns.register("string") { JsonPrimitive(arg(it, 0).asText()) }

            // --- dates ----------------------------------------------------------
            fns.register("now") { JsonPrimitive(System.currentTimeMillis()) }
            fns.register("formatdate") { args ->
                val raw = arg(args, 0)
                val pattern = arg(args, 1).asText().ifEmpty { "dd MMM yyyy" }
                val millis = raw.asNumber()?.toLong() ?: parseIso(raw.asText())
                    ?: return@register JsonPrimitive("")
                JsonPrimitive(
                    runCatching { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis)) }
                        .getOrDefault(""),
                )
            }

            return fns
        }

        private fun trimZeros(value: Double): String {
            val rounded = (value * 10).roundToLong() / 10.0
            return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
            else rounded.toString()
        }

        private fun parseIso(text: String): Long? {
            if (text.isBlank()) return null
            for (pattern in ISO_PATTERNS) {
                runCatching {
                    return SimpleDateFormat(pattern, Locale.US).parse(text)?.time
                }
            }
            return text.toLongOrNull()
        }

        private val ISO_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        )
    }
}
