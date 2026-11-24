package com.sdui.state.expression

import com.sdui.core.json.isTruthy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Name resolution for the outermost identifiers of an expression. */
public fun interface ValueScope {
    public fun resolve(name: String): JsonElement?
}

/**
 * Walks a compiled [Expr] against a [ValueScope].
 *
 * Evaluation is total: a missing path, a type mismatch or a division by zero all
 * yield `null` rather than throwing, because a broken binding must degrade to an
 * empty label — never to a crashed screen.
 */
public class Evaluator(
    private val scope: ValueScope,
    private val functions: ExpressionFunctions = ExpressionFunctions.Default,
) {
    public fun evaluate(expr: Expr): JsonElement? = when (expr) {
        is Expr.Literal -> expr.value.takeUnless { it is JsonNull }
        is Expr.Identifier -> scope.resolve(expr.name)?.takeUnless { it is JsonNull }
        is Expr.Member -> member(evaluate(expr.target), expr.name)
        is Expr.Index -> index(evaluate(expr.target), evaluate(expr.index))
        is Expr.Call -> functions.invoke(expr.name, expr.args.map { evaluate(it) })
        is Expr.Unary -> unary(expr)
        is Expr.Binary -> binary(expr)
        is Expr.Conditional ->
            if (evaluate(expr.condition).isTruthy()) evaluate(expr.ifTrue) else evaluate(expr.ifFalse)

        is Expr.ArrayLiteral -> JsonArray(expr.items.map { evaluate(it) ?: JsonNull })
        is Expr.ObjectLiteral ->
            JsonObject(expr.entries.associate { (key, value) -> key to (evaluate(value) ?: JsonNull) })
    }

    private fun member(target: JsonElement?, name: String): JsonElement? = when (target) {
        is JsonObject -> target[name]?.takeUnless { it is JsonNull }
            ?: pseudoProperty(target, name)

        is JsonArray -> pseudoProperty(target, name)
        is JsonPrimitive -> pseudoProperty(target, name)
        else -> null
    }

    /** Properties that exist on values without appearing in the payload. */
    private fun pseudoProperty(target: JsonElement, name: String): JsonElement? = when (name) {
        "length", "size", "count" -> when (target) {
            is JsonArray -> JsonPrimitive(target.size)
            is JsonObject -> JsonPrimitive(target.size)
            is JsonPrimitive -> JsonPrimitive(target.content.length)
        }

        "isEmpty" -> JsonPrimitive(!target.isTruthy())
        "isNotEmpty" -> JsonPrimitive(target.isTruthy())
        "first" -> (target as? JsonArray)?.firstOrNull()
        "last" -> (target as? JsonArray)?.lastOrNull()
        "keys" -> (target as? JsonObject)?.let { obj -> JsonArray(obj.keys.map { JsonPrimitive(it) }) }
        "values" -> (target as? JsonObject)?.let { obj -> JsonArray(obj.values.toList()) }
        else -> null
    }

    private fun index(target: JsonElement?, key: JsonElement?): JsonElement? = when (target) {
        is JsonArray -> key.asNumber()?.toInt()?.let { target.getOrNull(it) }
        is JsonObject -> target[key.asText()]
        else -> null
    }?.takeUnless { it is JsonNull }

    private fun unary(expr: Expr.Unary): JsonElement? {
        val value = evaluate(expr.operand)
        return when (expr.op) {
            UnaryOp.NOT -> JsonPrimitive(!value.isTruthy())
            UnaryOp.NEGATE -> value.asNumber()?.let { JsonPrimitive(-it) }
        }
    }

    private fun binary(expr: Expr.Binary): JsonElement? {
        // Short-circuit before evaluating the right operand.
        when (expr.op) {
            BinaryOp.AND -> {
                val left = evaluate(expr.left)
                return if (!left.isTruthy()) JsonPrimitive(false)
                else JsonPrimitive(evaluate(expr.right).isTruthy())
            }

            BinaryOp.OR -> {
                val left = evaluate(expr.left)
                return if (left.isTruthy()) JsonPrimitive(true)
                else JsonPrimitive(evaluate(expr.right).isTruthy())
            }

            BinaryOp.ELVIS -> return evaluate(expr.left) ?: evaluate(expr.right)
            else -> Unit
        }

        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.op) {
            BinaryOp.EQ -> JsonPrimitive(looseEquals(left, right))
            BinaryOp.NEQ -> JsonPrimitive(!looseEquals(left, right))
            BinaryOp.LT -> compare(left, right)?.let { JsonPrimitive(it < 0) }
            BinaryOp.LTE -> compare(left, right)?.let { JsonPrimitive(it <= 0) }
            BinaryOp.GT -> compare(left, right)?.let { JsonPrimitive(it > 0) }
            BinaryOp.GTE -> compare(left, right)?.let { JsonPrimitive(it >= 0) }

            BinaryOp.ADD -> {
                val ln = left.asNumber()
                val rn = right.asNumber()
                if (ln != null && rn != null && !left.isTextual() && !right.isTextual()) {
                    JsonPrimitive(ln + rn)
                } else {
                    JsonPrimitive(left.asText() + right.asText())
                }
            }

            BinaryOp.SUB -> arithmetic(left, right) { a, b -> a - b }
            BinaryOp.MUL -> arithmetic(left, right) { a, b -> a * b }
            // Division by zero yields null, which renders as an empty label rather
            // than "Infinity" or "NaN" leaking into the UI.
            BinaryOp.DIV -> divide(left, right) { a, b -> a / b }
            BinaryOp.MOD -> divide(left, right) { a, b -> a % b }

            BinaryOp.IN -> JsonPrimitive(
                when (right) {
                    is JsonArray -> right.any { looseEquals(it, left) }
                    is JsonObject -> right.containsKey(left.asText())
                    is JsonPrimitive -> right.content.contains(left.asText())
                    else -> false
                },
            )

            BinaryOp.MATCHES -> JsonPrimitive(
                runCatching { Regex(right.asText()).containsMatchIn(left.asText()) }.getOrDefault(false),
            )

            BinaryOp.AND, BinaryOp.OR, BinaryOp.ELVIS -> null // handled above
        }
    }

    private fun arithmetic(
        left: JsonElement?,
        right: JsonElement?,
        op: (Double, Double) -> Double,
    ): JsonElement? {
        val a = left.asNumber() ?: return null
        val b = right.asNumber() ?: return null
        return JsonPrimitive(op(a, b))
    }

    private fun divide(
        left: JsonElement?,
        right: JsonElement?,
        op: (Double, Double) -> Double,
    ): JsonElement? {
        val b = right.asNumber() ?: return null
        if (b == 0.0) return null
        return arithmetic(left, right, op)
    }

    private fun looseEquals(left: JsonElement?, right: JsonElement?): Boolean {
        if (left == null || left is JsonNull) return right == null || right is JsonNull
        if (right == null || right is JsonNull) return false
        val ln = left.asNumber()
        val rn = right.asNumber()
        if (ln != null && rn != null) return ln == rn
        val lb = (left as? JsonPrimitive)?.booleanOrNull
        val rb = (right as? JsonPrimitive)?.booleanOrNull
        if (lb != null || rb != null) return left.isTruthy() == right.isTruthy()
        return left.asText() == right.asText()
    }

    private fun compare(left: JsonElement?, right: JsonElement?): Int? {
        val ln = left.asNumber()
        val rn = right.asNumber()
        if (ln != null && rn != null) return ln.compareTo(rn)
        if (left == null || right == null) return null
        return left.asText().compareTo(right.asText())
    }
}

// ---------------------------------------------------------------------------
// Shared coercions
// ---------------------------------------------------------------------------

internal fun JsonElement?.isTextual(): Boolean =
    this is JsonPrimitive && isString && doubleOrNull == null

public fun JsonElement?.asNumber(): Double? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> doubleOrNull ?: content.toDoubleOrNull()
        ?: booleanOrNull?.let { if (it) 1.0 else 0.0 }

    else -> null
}

public fun JsonElement?.asText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> if (!isString) formatNumberLike(content) else content
    else -> toString()
}

/** Renders `12.0` as `12` so numeric bindings don't leak float formatting into UI. */
internal fun formatNumberLike(content: String): String {
    val d = content.toDoubleOrNull() ?: return content
    return if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15) d.toLong().toString() else content
}
