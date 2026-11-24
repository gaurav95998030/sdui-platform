package com.sdui.state

import com.sdui.core.json.SduiJson
import com.sdui.state.expression.ExpressionEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEngineTest {

    private val engine = ExpressionEngine()

    private val scope = BindingScope(
        screen = SduiJson.parseToJsonElement(
            """
            {
              "user":   { "firstName": "Ada", "age": 36, "isPremium": true, "tags": ["a","b","c"] },
              "wallet": { "balance": 1240.5, "currency": "INR" },
              "cart":   { "items": [ {"price": 100}, {"price": 250} ] },
              "empty":  [],
              "nothing": null
            }
            """.trimIndent(),
        ) as JsonObject,
        global = JsonObject(mapOf("tier" to JsonPrimitive("gold"))),
        env = JsonObject(mapOf("platform" to JsonPrimitive("android"))),
    )

    private fun eval(source: String) = engine.evaluate(engine.unwrap(source), scope)

    private fun text(source: String) = (engine.interpolate(source, scope) as? JsonPrimitive)?.content

    @Test
    fun `resolves nested paths`() {
        assertEquals("Ada", eval("user.firstName")?.let { (it as JsonPrimitive).content })
        assertEquals("gold", eval("tier")?.let { (it as JsonPrimitive).content })
        assertEquals("android", eval("env.platform")?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `missing paths evaluate to null rather than throwing`() {
        assertNull(eval("user.middleName"))
        assertNull(eval("nope.deeply.missing"))
        assertNull(eval("nothing"))
    }

    @Test
    fun `arithmetic and comparison`() {
        assertEquals(40.0, (eval("user.age + 4") as JsonPrimitive).content.toDouble(), 0.001)
        assertEquals(18.0, (eval("user.age / 2") as JsonPrimitive).content.toDouble(), 0.001)
        assertTrue((eval("wallet.balance > 1000") as JsonPrimitive).content.toBoolean())
        assertFalse((eval("user.age >= 40") as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `division by zero yields null instead of Infinity`() {
        assertNull(eval("user.age / 0"))
    }

    @Test
    fun `logical operators short circuit`() {
        assertTrue((eval("user.isPremium && wallet.balance > 0") as JsonPrimitive).content.toBoolean())
        assertTrue((eval("missing.thing || user.isPremium") as JsonPrimitive).content.toBoolean())
        assertFalse((eval("!user.isPremium") as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `ternary and elvis`() {
        assertEquals("Premium", (eval("user.isPremium ? 'Premium' : 'Free'") as JsonPrimitive).content)
        assertEquals("none", (eval("user.middleName ?: 'none'") as JsonPrimitive).content)
    }

    @Test
    fun `pseudo properties on collections and strings`() {
        assertEquals(3, (eval("user.tags.length") as JsonPrimitive).content.toInt())
        assertEquals(2, (eval("cart.items.size") as JsonPrimitive).content.toInt())
        assertEquals(3, (eval("user.firstName.length") as JsonPrimitive).content.toInt())
        assertTrue((eval("empty.isEmpty") as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `indexing arrays and objects`() {
        assertEquals("b", (eval("user.tags[1]") as JsonPrimitive).content)
        assertEquals(250.0, (eval("cart.items[1].price") as JsonPrimitive).content.toDouble(), 0.001)
    }

    @Test
    fun `built in functions`() {
        assertEquals("ADA", (eval("upper(user.firstName)") as JsonPrimitive).content)
        assertEquals("A", (eval("initials(user.firstName)") as JsonPrimitive).content)
        assertEquals(350.0, (eval("sum(cart.items, 'price')") as JsonPrimitive).content.toDouble(), 0.001)
        assertEquals("a, b, c", (eval("join(user.tags, ', ')") as JsonPrimitive).content)
        assertEquals("1.2K", (eval("compact(1240)") as JsonPrimitive).content)
    }

    @Test
    fun `in and matches operators`() {
        assertTrue((eval("'b' in user.tags") as JsonPrimitive).content.toBoolean())
        assertTrue((eval("user.firstName matches '^A'") as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `interpolation mixes literals and bindings`() {
        assertEquals("Hi Ada, you have 3 tags", text("Hi {{user.firstName}}, you have {{user.tags.length}} tags"))
    }

    @Test
    fun `a lone binding preserves the underlying json type`() {
        val result = engine.interpolate("{{user.tags}}", scope)
        assertTrue("expected a JsonArray, got $result", result is JsonArray)
        assertEquals(3, (result as JsonArray).size)
    }

    @Test
    fun `numeric interpolation does not leak float formatting`() {
        assertEquals("Age 36", text("Age {{user.age}}"))
    }

    @Test
    fun `a syntactically invalid expression degrades to null, never throws`() {
        assertNull(eval("user.((("))
        assertNull(engine.interpolate("{{user.(((}}", scope))
        // Mixed templates still produce text, with the broken fragment empty.
        assertEquals("a  b", text("a {{user.(((}} b"))
    }

    @Test
    fun `an explicit json null is treated as absent, not as the text null`() {
        assertNull(eval("nothing"))
        assertEquals("fallback", (eval("nothing ?: 'fallback'") as JsonPrimitive).content)
        assertEquals("", text("{{nothing}}").orEmpty())
    }

    @Test
    fun `escaped braces are emitted literally`() {
        assertEquals("{{user.firstName}}", text("\\{{user.firstName}}"))
    }

    @Test
    fun `resolveTree substitutes strings and leaves other types intact`() {
        val props = SduiJson.parseToJsonElement(
            """{ "text": "Hello {{user.firstName}}", "count": 3, "flag": true, "nested": { "t": "{{tier}}" } }""",
        ) as JsonObject
        val resolved = engine.resolveTree(props, scope) as JsonObject
        assertEquals("Hello Ada", (resolved["text"] as JsonPrimitive).content)
        assertEquals("3", (resolved["count"] as JsonPrimitive).content)
        assertEquals("gold", (resolved["nested"] as JsonObject).let { (it["t"] as JsonPrimitive).content })
    }

    @Test
    fun `compiled expressions are cached`() {
        val first = engine.compile("user.age + 1")
        val second = engine.compile("user.age + 1")
        assertTrue("expected the cached AST instance", first === second)
    }
}
