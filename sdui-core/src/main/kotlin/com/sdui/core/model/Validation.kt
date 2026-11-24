package com.sdui.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
public enum class ValidationType {
    @SerialName("required") REQUIRED,
    @SerialName("email") EMAIL,
    @SerialName("phone") PHONE,
    @SerialName("url") URL,
    @SerialName("numeric") NUMERIC,
    @SerialName("regex") REGEX,
    @SerialName("minLength") MIN_LENGTH,
    @SerialName("maxLength") MAX_LENGTH,
    @SerialName("min") MIN,
    @SerialName("max") MAX,
    @SerialName("equals") EQUALS,
    @SerialName("matchesField") MATCHES_FIELD,
}

/**
 * One server-defined validation constraint. The SDK owns evaluation so that
 * error copy and rules can change without an app release.
 *
 * ```json
 * { "type": "minLength", "value": 8, "message": "At least 8 characters" }
 * ```
 */
@Serializable
public data class ValidationRule(
    val type: ValidationType,
    val value: JsonPrimitive? = null,
    val message: String? = null,
)

public data class ValidationResult(
    val valid: Boolean,
    val message: String? = null,
) {
    public companion object {
        public val Valid: ValidationResult = ValidationResult(true)
        public fun invalid(message: String): ValidationResult = ValidationResult(false, message)
    }
}
