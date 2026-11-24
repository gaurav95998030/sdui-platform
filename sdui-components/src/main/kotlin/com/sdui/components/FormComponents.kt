package com.sdui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sdui.actions.FieldRegistration
import com.sdui.actions.FormRegistry
import com.sdui.core.json.SduiJson
import com.sdui.core.json.atPath
import com.sdui.core.model.ValidationRule
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.array
import com.sdui.renderer.bool
import com.sdui.renderer.buildModifier
import com.sdui.renderer.int
import com.sdui.renderer.str
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared plumbing for every input: where the value lives, what validates it, and
 * how its error surfaces.
 *
 * The value is stored in screen state at [path] rather than in local composable
 * state, so bindings elsewhere on the screen (`{{form.email}}`), validation
 * actions and API bodies all see the same source of truth.
 */
@Composable
private fun rememberField(
    node: ResolvedNode,
    scope: RenderScope,
): FieldBinding {
    val runtime = LocalScreenRuntime.current
    val name = node.props.str("name") ?: node.id ?: scope.path
    val path = node.props.str("path") ?: node.props.str("bind") ?: "form.$name"
    val formId = node.props.str("form") ?: FormRegistry.DEFAULT_FORM
    val errorPath = node.props.str("errorPath") ?: "errors.$name"

    val rules = remember(node.props) {
        node.props["rules"]?.let { element ->
            runCatching {
                SduiJson.decodeFromJsonElement(ListSerializer(ValidationRule.serializer()), element)
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    // Registration is scoped to the composition: fields that scroll out of a lazy
    // list stop being validated, which matches what the user can actually see.
    DisposableEffect(runtime.entryId, formId, name, rules) {
        runtime.forms.register(
            entryId = runtime.entryId,
            formId = formId,
            field = FieldRegistration(
                name = name,
                statePath = path,
                rules = rules,
                label = node.props.str("label"),
                required = rules.any { it.type == com.sdui.core.model.ValidationType.REQUIRED },
            ),
        )
        onDispose { runtime.forms.unregister(runtime.entryId, formId, name) }
    }

    val state by runtime.screenState.state.collectAsState()
    return FieldBinding(
        name = name,
        path = path,
        value = state.atPath(path)?.let { (it as? JsonPrimitive)?.content ?: it.toString() } ?: "",
        error = state.atPath(errorPath)?.let { (it as? JsonPrimitive)?.content },
        onChange = { newValue ->
            runtime.screenState.set(path, newValue)
            runtime.screenState.remove(errorPath)
            runtime.dispatch(node, "onChange", scope.locals + mapOf("value" to JsonPrimitive(newValue)))
        },
    )
}

private data class FieldBinding(
    val name: String,
    val path: String,
    val value: String,
    val error: String?,
    val onChange: (String) -> Unit,
)

/**
 * `textField` / `password` / `number` / `otp` — one renderer, keyboard and visual
 * transformation selected by props, because they differ only in input handling.
 */
public class TextFieldRenderer(
    private val defaultKeyboard: KeyboardType = KeyboardType.Text,
    private val defaultSecure: Boolean = false,
) : ComponentRenderer {

    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val field = rememberField(node, scope)
        var revealed by remember { mutableStateOf(false) }

        val secure = node.props.bool("secure", defaultSecure)
        val keyboard = when (node.props.str("keyboard")) {
            "email" -> KeyboardType.Email
            "number", "numeric" -> KeyboardType.Number
            "phone" -> KeyboardType.Phone
            "password" -> KeyboardType.Password
            "decimal" -> KeyboardType.Decimal
            "url" -> KeyboardType.Uri
            null -> defaultKeyboard
            else -> KeyboardType.Text
        }

        val trailing: (@Composable () -> Unit)? = when {
            secure -> {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        MaterialIcons.resolve(if (revealed) "hidden" else "visible")?.let {
                            Icon(it, contentDescription = if (revealed) "Hide" else "Show")
                        }
                    }
                }
            }

            node.props.str("trailingIcon") != null -> {
                {
                    MaterialIcons.resolve(node.props.str("trailingIcon")!!)
                        ?.let { Icon(it, contentDescription = null) }
                }
            }

            else -> null
        }

        val modifier = buildModifier(node.modifier, scope.parent)
            .let { if (node.modifier.width == null) it.fillMaxWidth() else it }

        val common: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(
                value = field.value,
                onValueChange = { text ->
                    val max = node.props.int("maxLength", Int.MAX_VALUE)
                    field.onChange(if (text.length > max) text.take(max) else text)
                },
                modifier = m,
                enabled = node.modifier.enabled && node.props.bool("enabled", true),
                readOnly = node.props.bool("readOnly"),
                label = node.props.str("label")?.let { { Text(it) } },
                placeholder = node.props.str("placeholder")?.let { { Text(it) } },
                supportingText = (field.error ?: node.props.str("helperText"))?.let { { Text(it) } },
                isError = field.error != null,
                singleLine = node.props.bool("singleLine", true),
                maxLines = node.props.int("maxLines", 1),
                visualTransformation = if (secure && !revealed) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboard,
                    imeAction = if (node.props.bool("submitOnDone")) ImeAction.Done else ImeAction.Next,
                ),
                leadingIcon = node.props.str("leadingIcon")?.let { name ->
                    { MaterialIcons.resolve(name)?.let { Icon(it, contentDescription = null) } }
                },
                trailingIcon = trailing,
            )
        }
        common(modifier)
    }
}

/** `checkbox` — boolean input with an inline label. */
public object CheckboxRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val field = rememberField(node, scope)
        val checked = field.value.equals("true", ignoreCase = true)
        Row(
            modifier = buildModifier(node.modifier, scope.parent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { field.onChange(it.toString()) },
                enabled = node.modifier.enabled,
            )
            node.props.str("label")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        field.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** `switch` — same contract as checkbox, different affordance. */
public object SwitchRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val field = rememberField(node, scope)
        Row(
            modifier = buildModifier(node.modifier, scope.parent).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            node.props.str("label")?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Switch(
                checked = field.value.equals("true", ignoreCase = true),
                onCheckedChange = { field.onChange(it.toString()) },
                enabled = node.modifier.enabled,
            )
        }
    }
}

/** `radioGroup` — single choice from `options: [{ label, value }]`. */
public object RadioGroupRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val field = rememberField(node, scope)
        val options = node.props.array("options").orEmpty()
        Column(modifier = buildModifier(node.modifier, scope.parent)) {
            node.props.str("label")?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
            }
            options.forEach { option ->
                val value = option.optionValue()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RadioButton(
                        selected = field.value == value,
                        onClick = { field.onChange(value) },
                        enabled = node.modifier.enabled,
                    )
                    Text(option.optionLabel(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            field.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** `dropdown` — Material exposed dropdown over the same `options` shape. */
public object DropdownRenderer : ComponentRenderer {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val field = rememberField(node, scope)
        val options = node.props.array("options").orEmpty()
        var expanded by remember { mutableStateOf(false) }
        val selectedLabel = options.firstOrNull { it.optionValue() == field.value }?.optionLabel()
            ?: field.value

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (node.modifier.enabled) expanded = it },
            modifier = buildModifier(node.modifier, scope.parent).fillMaxWidth(),
        ) {
            TextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = node.props.str("label")?.let { { Text(it) } },
                placeholder = node.props.str("placeholder")?.let { { Text(it) } },
                isError = field.error != null,
                supportingText = field.error?.let { { Text(it) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.optionLabel()) },
                        onClick = {
                            field.onChange(option.optionValue())
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** `datePicker` — stores an ISO date string; displays it with `displayFormat`. */
public object DatePickerRenderer : ComponentRenderer {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val field = rememberField(node, scope)
        var showing by remember { mutableStateOf(false) }
        val displayFormat = node.props.str("displayFormat") ?: "dd MMM yyyy"

        val display = remember(field.value, displayFormat) {
            runCatching {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(field.value)
                parsed?.let { SimpleDateFormat(displayFormat, Locale.getDefault()).format(it) }
            }.getOrNull() ?: field.value
        }

        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = node.props.str("label")?.let { { Text(it) } },
            placeholder = node.props.str("placeholder")?.let { { Text(it) } },
            isError = field.error != null,
            supportingText = field.error?.let { { Text(it) } },
            trailingIcon = {
                MaterialIcons.resolve("calendar")?.let { Icon(it, contentDescription = "Pick a date") }
            },
            modifier = buildModifier(
                spec = node.modifier,
                parent = scope.parent,
                onClick = { showing = true },
            ).fillMaxWidth(),
        )

        if (showing) {
            val pickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showing = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            field.onChange(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis)))
                        }
                        showing = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showing = false }) { Text("Cancel") } },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

/** `otp` — fixed-length numeric entry rendered as separated boxes. */
public object OtpRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val field = rememberField(node, scope)
        val length = node.props.int("length", 6)

        // Auto-submit is an effect, not a callback side effect: firing the action
        // from inside onValueChange would run it during composition.
        LaunchedEffect(field.value, length) {
            if (field.value.length == length) {
                runtime.dispatch(
                    node, "onComplete",
                    scope.locals + mapOf("value" to JsonPrimitive(field.value)),
                )
            }
        }

        Column(modifier = buildModifier(node.modifier, scope.parent)) {
            node.props.str("label")?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
            }
            OutlinedTextField(
                value = field.value,
                onValueChange = { text -> field.onChange(text.filter(Char::isDigit).take(length)) },
                singleLine = true,
                isError = field.error != null,
                supportingText = field.error?.let { { Text(it) } },
                placeholder = { Text("•".repeat(length)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.width((length * 44).dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------

private fun kotlinx.serialization.json.JsonElement.optionValue(): String = when (this) {
    is JsonObject -> (this["value"] as? JsonPrimitive)?.content
        ?: (this["id"] as? JsonPrimitive)?.content
        ?: (this["label"] as? JsonPrimitive)?.content.orEmpty()

    is JsonPrimitive -> content
    else -> ""
}

private fun kotlinx.serialization.json.JsonElement.optionLabel(): String = when (this) {
    is JsonObject -> (this["label"] as? JsonPrimitive)?.content
        ?: (this["title"] as? JsonPrimitive)?.content
        ?: optionValue()

    is JsonPrimitive -> content
    else -> ""
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
