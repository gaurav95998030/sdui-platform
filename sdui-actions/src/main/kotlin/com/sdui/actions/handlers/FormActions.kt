package com.sdui.actions.handlers

import com.sdui.actions.ActionContext
import com.sdui.actions.ActionHandler
import com.sdui.actions.FormRegistry
import com.sdui.actions.ResolvedAction
import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.state.Validators
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `validateForm` — runs every registered field's server-defined rules.
 *
 * Errors are written to `<errorPath>.<field>` (default `errors`) so the field
 * components, which already bind to that path, light up without any extra wiring.
 *
 * ```json
 * { "type": "validateForm", "params": { "form": "signup" },
 *   "then": [ { "type": "apiCall", "params": { "api": "signup" } } ] }
 * ```
 */
public object ValidateFormHandler : ActionHandler {

    override suspend fun handle(
        action: ResolvedAction,
        context: ActionContext,
    ): SduiResult<JsonElement?> {
        val formId = action.string("form")
        val fields = if (formId == null) {
            context.forms.allFields(context.entryId)
        } else {
            context.forms.fields(context.entryId, formId)
        }

        if (fields.isEmpty()) {
            context.logger.w(
                "Forms",
                "validateForm found no registered fields for '${formId ?: "any form"}'",
            )
            return SduiResult.success(JsonPrimitive(true))
        }

        val errorPath = action.string("errorPath", "errors")!!
        val snapshot = context.screenState.snapshot
        val failures = mutableMapOf<String, String>()

        for (field in fields) {
            val value = context.screenState.get(field.statePath)
            val siblings = fields.associate { it.name to (context.screenState.get(it.statePath) ?: JsonPrimitive("")) }
            val result = Validators.validate(value, field.rules, JsonObject(siblings))
            val target = "$errorPath.${field.name}"
            if (result.valid) {
                context.screenState.remove(target)
            } else {
                failures[field.name] = result.message.orEmpty()
                context.screenState.set(target, result.message.orEmpty())
            }
        }

        // Marks the form as submitted so fields can switch from "pristine" to
        // "show errors" without every field tracking that itself.
        context.screenState.set("${errorPath}__touched", true)

        return if (failures.isEmpty()) {
            SduiResult.success(JsonPrimitive(true))
        } else {
            action.string("focusFirstError")?.let {
                context.effects.emit(com.sdui.actions.UiEffect.ScrollTo(failures.keys.first()))
            }
            SduiResult.failure(SduiError.Validation(failures))
        }
    }
}

/**
 * `submitForm` — validate, then call the named API with the form's values as the
 * body. A convenience over `validateForm` + `apiCall` for the common case.
 */
public class SubmitFormHandler(
    private val apiHandler: ApiCallHandler = ApiCallHandler(),
) : ActionHandler {

    override suspend fun handle(
        action: ResolvedAction,
        context: ActionContext,
    ): SduiResult<JsonElement?> {
        val validation = ValidateFormHandler.handle(action, context)
        if (validation is SduiResult.Failure) return validation

        val formId = action.string("form") ?: FormRegistry.DEFAULT_FORM
        val fields = context.forms.fields(context.entryId, formId)
            .ifEmpty { context.forms.allFields(context.entryId) }

        val payload = JsonObject(
            fields.associate { field ->
                field.name to (context.screenState.get(field.statePath) ?: JsonPrimitive(""))
            },
        )

        val withBody = ResolvedAction(
            type = action.type,
            params = JsonObject(action.params + mapOf("body" to payload)),
            spec = action.spec,
        )
        return apiHandler.handle(withBody, context)
    }
}
