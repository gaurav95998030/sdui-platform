package com.sdui.parser

import com.sdui.core.SduiResult
import com.sdui.core.model.Dimension
import com.sdui.core.model.HttpMethod
import com.sdui.core.model.NavigationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenParserTest {

    private val parser = ScreenParser()

    private val minimal = """
        { "id": "home", "root": { "type": "column" } }
    """.trimIndent()

    @Test
    fun `parses a minimal screen`() {
        val result = parser.parseScreen(minimal)
        assertTrue(result is SduiResult.Success)
        val screen = result.getOrNull()!!
        assertEquals("home", screen.id)
        assertEquals("column", screen.root.type)
    }

    @Test
    fun `accepts both a bare screen and an envelope`() {
        assertTrue(parser.parseEnvelope(minimal) is SduiResult.Success)
        val enveloped = """{ "screen": $minimal, "etag": "abc", "ttlSeconds": 60 }"""
        val result = parser.parseEnvelope(enveloped).getOrNull()!!
        assertEquals("abc", result.etag)
        assertEquals(60L, result.ttlSeconds)
    }

    @Test
    fun `unknown keys from a newer server are ignored, not fatal`() {
        val futureSchema = """
            { "id": "home", "somethingNew": { "a": 1 },
              "root": { "type": "column", "brandNewField": true } }
        """.trimIndent()
        assertTrue(parser.parseScreen(futureSchema) is SduiResult.Success)
    }

    @Test
    fun `unknown enum values coerce to the default instead of throwing`() {
        val unknownEnums = """
            { "id": "home",
              "apis": { "x": { "url": "https://e.com", "method": "TRACE" } },
              "root": { "type": "column", "modifier": { "alignment": "diagonal" } } }
        """.trimIndent()
        val screen = parser.parseScreen(unknownEnums).getOrNull()
        assertNotNull("payload with unknown enums should still parse", screen)
        // Coercion targets each property's declared default: HttpMethod defaults to
        // GET, while `alignment` is nullable and therefore falls back to "unset".
        assertEquals(HttpMethod.GET, screen!!.apis["x"]!!.method)
        assertNull(screen.root.modifier.alignment)
    }

    @Test
    fun `dimensions accept numbers, keywords and percentages`() {
        val dims = """
            { "id": "d", "root": { "type": "column",
              "modifier": { "width": "match_parent", "height": 120, "size": "50%" } } }
        """.trimIndent()
        val modifier = parser.parseScreen(dims).getOrNull()!!.root.modifier
        assertEquals(Dimension.Fill, modifier.width)
        assertEquals(Dimension.Points(120f), modifier.height)
        assertEquals(Dimension.Fraction(0.5f), modifier.size)
    }

    @Test
    fun `padding accepts a scalar, shorthand object and full object`() {
        fun paddingOf(json: String) = parser
            .parseNode("""{ "type": "column", "modifier": { "padding": $json } }""")
            .getOrNull()!!.modifier.padding!!

        assertEquals(16f, paddingOf("16").start, 0.001f)
        assertEquals(16f, paddingOf("16").bottom, 0.001f)

        val shorthand = paddingOf("""{ "horizontal": 12, "vertical": 4 }""")
        assertEquals(12f, shorthand.start, 0.001f)
        assertEquals(4f, shorthand.top, 0.001f)

        val full = paddingOf("""{ "start": 1, "top": 2, "end": 3, "bottom": 4 }""")
        assertEquals(3f, full.end, 0.001f)
    }

    @Test
    fun `malformed json returns a failure rather than throwing`() {
        val result = parser.parseScreen("{ this is not json")
        assertTrue(result is SduiResult.Failure)
        assertEquals("parse", result.errorOrNull()!!.code)
    }

    @Test
    fun `actions parse with guards and chains`() {
        val withChain = """
            { "id": "s", "root": { "type": "button",
              "actions": { "onClick": {
                "type": "apiCall", "if": "{{form.valid}}",
                "params": { "api": "save" },
                "then": [ { "type": "navigate", "params": { "mode": "pop" } } ],
                "else": [ { "type": "showToast", "params": { "message": "fix it" } } ] } } } }
        """.trimIndent()
        val action = parser.parseScreen(withChain).getOrNull()!!.root.actions["onClick"]!!
        assertEquals("apiCall", action.type)
        assertEquals("{{form.valid}}", action.condition)
        assertEquals("navigate", action.then.single().type)
        assertEquals("showToast", action.otherwise.single().type)
    }

    @Test
    fun `round trips through encode and decode`() {
        val original = parser.parseScreen(minimal).getOrNull()!!
        val encoded = parser.encodeScreen(original)
        assertEquals(original, parser.parseScreen(encoded).getOrNull())
    }
}

class SchemaValidatorTest {

    private val parser = ScreenParser()
    private val validator = SchemaValidator()

    private fun validate(json: String, components: Set<String> = emptySet(), actions: Set<String> = emptySet()) =
        validator.validate(parser.parseScreen(json).getOrNull()!!, components, actions)

    @Test
    fun `a clean screen produces no issues`() {
        val report = validate("""{ "id": "home", "root": { "type": "column" } }""", setOf("column"))
        assertTrue(report.describe(), report.isClean)
    }

    @Test
    fun `unknown component types are errors`() {
        val report = validate(
            """{ "id": "h", "root": { "type": "column", "children": [ { "type": "hologram" } ] } }""",
            components = setOf("column"),
        )
        assertTrue(report.hasErrors)
        assertTrue(report.errors.single().message.contains("hologram"))
    }

    @Test
    fun `apiCall referencing an undeclared api is an error`() {
        val report = validate(
            """
            { "id": "h", "root": { "type": "button",
              "actions": { "onClick": { "type": "apiCall", "params": { "api": "ghost" } } } } }
            """.trimIndent(),
            components = setOf("button"),
            actions = setOf("apiCall"),
        )
        assertTrue(report.hasErrors)
        assertTrue(report.errors.any { it.message.contains("ghost") })
    }

    @Test
    fun `navigate without a target is an error, but pop is fine`() {
        val bad = validate(
            """{ "id": "h", "root": { "type": "button",
                 "actions": { "onClick": { "type": "navigate", "params": {} } } } }""",
            actions = setOf("navigate"),
        )
        assertTrue(bad.hasErrors)

        val good = validate(
            """{ "id": "h", "root": { "type": "button",
                 "actions": { "onClick": { "type": "navigate", "params": { "mode": "pop" } } } } }""",
            actions = setOf("navigate"),
        )
        assertFalse(good.hasErrors)
    }

    @Test
    fun `duplicate ids warn without failing the screen`() {
        val report = validate(
            """
            { "id": "h", "root": { "type": "column", "children": [
                { "type": "text", "id": "dupe" }, { "type": "text", "id": "dupe" } ] } }
            """.trimIndent(),
        )
        assertFalse(report.hasErrors)
        assertEquals(1, report.warnings.size)
    }

    @Test
    fun `weight outside a flex container warns`() {
        val report = validate(
            """{ "id": "h", "root": { "type": "card", "children": [
                 { "type": "text", "modifier": { "weight": 1 } } ] } }""",
        )
        assertTrue(report.warnings.any { it.message.contains("weight") })
    }

    @Test
    fun `runaway nesting is rejected rather than overflowing the stack`() {
        val deep = generateSequence(0) { it + 1 }.take(80)
            .fold("""{ "type": "text" }""") { acc, _ -> """{ "type": "column", "children": [$acc] }""" }
        val report = validate("""{ "id": "h", "root": $deep }""")
        assertTrue(report.hasErrors)
        assertTrue(report.errors.any { it.message.contains("depth") })
    }

    @Test
    fun `parseValidated in strict mode fails on an unknown component`() {
        val result = parser.parseValidated(
            raw = """{ "id": "h", "root": { "type": "hologram" } }""",
            knownComponents = setOf("column"),
            strict = true,
        )
        assertTrue(result is SduiResult.Failure)
    }

    @Test
    fun `parseValidated in lenient mode still returns the screen with a report`() {
        val result = parser.parseValidated(
            raw = """{ "id": "h", "root": { "type": "hologram" } }""",
            knownComponents = setOf("column"),
            strict = false,
        )
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.report.hasErrors)
        assertEquals("h", parsed.screen.id)
    }

    @Test
    fun `navigation modes deserialize from their wire names`() {
        val screen = parser.parseScreen(
            """{ "id": "h", "root": { "type": "column" }, "onLoad": [] }""",
        ).getOrNull()
        assertNotNull(screen)
        assertNull(screen!!.appBar)
        assertEquals(NavigationMode.PUSH, NavigationMode.entries.first())
    }
}
