package com.sdui.state

import com.sdui.core.model.ValidationResult
import com.sdui.core.model.ValidationRule
import com.sdui.core.model.ValidationType
import com.sdui.state.expression.asNumber
import com.sdui.state.expression.asText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Evaluates the server-defined validation rules attached to form fields.
 *
 * Rules live in the screen JSON, so tightening a password policy or changing
 * error copy ships without an app release; the SDK only owns *how* each rule
 * type is checked.
 */
public object Validators {

    private val EMAIL = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE = Regex("^[+]?[0-9 ()-]{6,20}$")
    private val URL = Regex("^https?://[^\\s]+$")
    private val NUMERIC = Regex("^-?\\d+(\\.\\d+)?$")

    public fun validate(
        value: JsonElement?,
        rules: List<ValidationRule>,
        /** Sibling field values, for `matchesField`. */
        siblings: JsonObject? = null,
    ): ValidationResult {
        for (rule in rules) {
            val result = check(value, rule, siblings)
            if (!result.valid) return result
        }
        return ValidationResult.Valid
    }

    private fun check(
        value: JsonElement?,
        rule: ValidationRule,
        siblings: JsonObject?,
    ): ValidationResult {
        val text = value.asText()
        val fail = { fallback: String -> ValidationResult.invalid(rule.message ?: fallback) }

        // Every rule except `required` passes on an empty value, so optional fields
        // are not rejected for merely being blank.
        if (text.isBlank() && rule.type != ValidationType.REQUIRED) return ValidationResult.Valid

        return when (rule.type) {
            ValidationType.REQUIRED ->
                if (text.isBlank()) fail("This field is required") else ValidationResult.Valid

            ValidationType.EMAIL ->
                if (EMAIL.matches(text)) ValidationResult.Valid else fail("Enter a valid email address")

            ValidationType.PHONE ->
                if (PHONE.matches(text)) ValidationResult.Valid else fail("Enter a valid phone number")

            ValidationType.URL ->
                if (URL.matches(text)) ValidationResult.Valid else fail("Enter a valid URL")

            ValidationType.NUMERIC ->
                if (NUMERIC.matches(text)) ValidationResult.Valid else fail("Numbers only")

            ValidationType.REGEX -> {
                val pattern = rule.value.asText()
                val matches = runCatching { Regex(pattern).matches(text) }.getOrDefault(false)
                if (matches) ValidationResult.Valid else fail("Invalid format")
            }

            ValidationType.MIN_LENGTH -> {
                val min = rule.value.asNumber()?.toInt() ?: 0
                if (text.length >= min) ValidationResult.Valid
                else fail("Must be at least $min characters")
            }

            ValidationType.MAX_LENGTH -> {
                val max = rule.value.asNumber()?.toInt() ?: Int.MAX_VALUE
                if (text.length <= max) ValidationResult.Valid
                else fail("Must be at most $max characters")
            }

            ValidationType.MIN -> {
                val min = rule.value.asNumber() ?: Double.NEGATIVE_INFINITY
                val number = value.asNumber() ?: text.toDoubleOrNull()
                if (number != null && number >= min) ValidationResult.Valid
                else fail("Must be at least ${rule.value.asText()}")
            }

            ValidationType.MAX -> {
                val max = rule.value.asNumber() ?: Double.POSITIVE_INFINITY
                val number = value.asNumber() ?: text.toDoubleOrNull()
                if (number != null && number <= max) ValidationResult.Valid
                else fail("Must be at most ${rule.value.asText()}")
            }

            ValidationType.EQUALS ->
                if (text == rule.value.asText()) ValidationResult.Valid else fail("Value does not match")

            ValidationType.MATCHES_FIELD -> {
                val other = siblings?.get(rule.value.asText()).asText()
                if (text == other) ValidationResult.Valid else fail("Values do not match")
            }
        }
    }
}
