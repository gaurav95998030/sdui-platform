package com.sdui.actions

import com.sdui.core.model.ValidationRule
import java.util.concurrent.ConcurrentHashMap

/**
 * A field's participation in a form, published by the component while it is
 * composed and withdrawn when it leaves.
 */
public data class FieldRegistration(
    val name: String,
    /** State path holding the field's current value, e.g. `form.email`. */
    val statePath: String,
    val rules: List<ValidationRule> = emptyList(),
    val label: String? = null,
    val required: Boolean = false,
)

/**
 * Connects declaratively rendered inputs to the `validateForm` / `submitForm`
 * actions.
 *
 * Rules live on the field node where an author expects them, yet validation is
 * triggered by an action somewhere else in the tree. Rather than couple those two
 * through the JSON, fields register here for as long as they are on screen, and
 * the action asks the registry what to validate.
 */
public class FormRegistry {

    private val forms = ConcurrentHashMap<String, MutableMap<String, FieldRegistration>>()

    public fun register(entryId: String, formId: String, field: FieldRegistration) {
        forms.getOrPut(key(entryId, formId)) { ConcurrentHashMap() }[field.name] = field
    }

    public fun unregister(entryId: String, formId: String, fieldName: String) {
        forms[key(entryId, formId)]?.remove(fieldName)
    }

    public fun fields(entryId: String, formId: String): List<FieldRegistration> =
        forms[key(entryId, formId)]?.values?.toList().orEmpty()

    /** Every field on the screen, for `validateForm` with no explicit form id. */
    public fun allFields(entryId: String): List<FieldRegistration> =
        forms.entries.filter { it.key.startsWith("$entryId/") }
            .flatMap { it.value.values }

    public fun releaseEntry(entryId: String) {
        forms.keys.filter { it.startsWith("$entryId/") }.forEach(forms::remove)
    }

    private fun key(entryId: String, formId: String) = "$entryId/$formId"

    public companion object {
        public const val DEFAULT_FORM: String = "default"
    }
}
