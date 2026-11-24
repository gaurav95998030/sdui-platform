package com.sdui.parser

import com.sdui.core.SduiError
import com.sdui.core.SduiResult
import com.sdui.core.json.SduiJson
import com.sdui.core.json.SduiJsonPretty
import com.sdui.core.model.ActionSpec
import com.sdui.core.model.ScreenEnvelope
import com.sdui.core.model.SduiNode
import com.sdui.core.model.SduiScreen
import com.sdui.core.model.ThemeSpec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Turns wire JSON into the typed model. Every entry point returns [SduiResult];
 * a malformed payload can never propagate an exception into composition.
 *
 * The parser is intentionally *stateless and pure* — no Android types, no I/O —
 * which makes the whole schema unit-testable on the JVM.
 */
public class ScreenParser(
    private val validator: SchemaValidator = SchemaValidator(),
) {
    /** Accepts either a bare screen object or a `{ "screen": { ... } }` envelope. */
    public fun parseEnvelope(raw: String): SduiResult<ScreenEnvelope> = SduiResult.catching {
        val element = SduiJson.parseToJsonElement(raw)
        val obj = element as? JsonObject
            ?: throw SduiError.Parse("Top-level screen payload must be a JSON object")
        if (obj.containsKey("screen")) {
            SduiJson.decodeFromJsonElement(ScreenEnvelope.serializer(), obj)
        } else {
            ScreenEnvelope(screen = SduiJson.decodeFromJsonElement(SduiScreen.serializer(), obj))
        }
    }

    public fun parseScreen(raw: String): SduiResult<SduiScreen> = SduiResult.catching {
        SduiJson.decodeFromString(SduiScreen.serializer(), raw)
    }

    public fun parseScreen(element: JsonElement): SduiResult<SduiScreen> = SduiResult.catching {
        SduiJson.decodeFromJsonElement(SduiScreen.serializer(), element)
    }

    public fun parseNode(raw: String): SduiResult<SduiNode> = SduiResult.catching {
        SduiJson.decodeFromString(SduiNode.serializer(), raw)
    }

    public fun parseAction(element: JsonElement): SduiResult<ActionSpec> = SduiResult.catching {
        SduiJson.decodeFromJsonElement(ActionSpec.serializer(), element)
    }

    public fun parseTheme(raw: String): SduiResult<ThemeSpec> = SduiResult.catching {
        SduiJson.decodeFromString(ThemeSpec.serializer(), raw)
    }

    /**
     * Parses and validates in one step. [knownComponents]/[knownActions] come from
     * the live registries, so validation reflects what this build can actually render.
     */
    public fun parseValidated(
        raw: String,
        knownComponents: Set<String> = emptySet(),
        knownActions: Set<String> = emptySet(),
        strict: Boolean = false,
    ): SduiResult<ParsedScreen> {
        val envelope = parseEnvelope(raw)
        return when (envelope) {
            is SduiResult.Failure -> envelope
            is SduiResult.Success -> {
                val report = validator.validate(envelope.value.screen, knownComponents, knownActions)
                if (strict && report.hasErrors) {
                    SduiResult.failure(SduiError.Parse(report.describe()))
                } else {
                    SduiResult.success(ParsedScreen(envelope.value, report))
                }
            }
        }
    }

    public fun encodeScreen(screen: SduiScreen, pretty: Boolean = false): String =
        (if (pretty) SduiJsonPretty else SduiJson).encodeToString(SduiScreen.serializer(), screen)

    public fun encodeNode(node: SduiNode, pretty: Boolean = false): String =
        (if (pretty) SduiJsonPretty else SduiJson).encodeToString(SduiNode.serializer(), node)
}

public data class ParsedScreen(
    val envelope: ScreenEnvelope,
    val report: ValidationReport,
) {
    val screen: SduiScreen get() = envelope.screen
}
