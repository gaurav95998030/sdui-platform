package com.sdui.state

import com.sdui.core.model.ValidationRule
import com.sdui.core.model.ValidationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateStoreTest {

    @Test
    fun `writes and reads nested paths`() {
        val store = StateStore("test")
        store.set("user.profile.name", "Ada")
        assertEquals("Ada", store.getString("user.profile.name"))
        assertEquals("Ada", (store.get("user.profile.name") as JsonPrimitive).content)
    }

    @Test
    fun `writing a deep path preserves sibling branches`() {
        val store = StateStore(
            "test",
            buildJsonObject {
                put("user", buildJsonObject { put("name", "Ada"); put("email", "a@b.c") })
                put("other", "keep me")
            },
        )
        store.set("user.name", "Grace")
        assertEquals("Grace", store.getString("user.name"))
        assertEquals("a@b.c", store.getString("user.email"))
        assertEquals("keep me", store.getString("other"))
    }

    @Test
    fun `toggle flips truthiness and creates missing paths as true`() {
        val store = StateStore("test")
        store.toggle("ui.expanded")
        assertTrue(store.getBoolean("ui.expanded"))
        store.toggle("ui.expanded")
        assertFalse(store.getBoolean("ui.expanded"))
    }

    @Test
    fun `remove drops only the target path`() {
        val store = StateStore("test")
        store.set("errors.email", "bad")
        store.set("errors.name", "bad")
        store.remove("errors.email")
        assertNull(store.get("errors.email"))
        assertEquals("bad", store.getString("errors.name"))
    }

    @Test
    fun `merge deep-merges without clobbering untouched branches`() {
        val store = StateStore("test", buildJsonObject { put("a", buildJsonObject { put("x", 1) }) })
        store.merge(buildJsonObject { put("a", buildJsonObject { put("y", 2) }) })
        assertEquals("1", store.getString("a.x"))
        assertEquals("2", store.getString("a.y"))
    }

    @Test
    fun `observe emits only on actual change`() = runTest {
        val store = StateStore("test")
        store.set("count", 1)
        assertEquals("1", (store.observe("count").first() as JsonPrimitive).content)
    }

    @Test
    fun `array indices are addressable`() {
        val store = StateStore(
            "test",
            com.sdui.core.json.SduiJson.parseToJsonElement(
                """{ "items": [ { "id": "a" }, { "id": "b" } ] }""",
            ) as JsonObject,
        )
        assertEquals("b", store.getString("items[1].id"))
    }
}

class ValidatorsTest {

    private fun rule(type: ValidationType, value: Any? = null, message: String? = null) =
        ValidationRule(
            type = type,
            value = when (value) {
                null -> null
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            },
            message = message,
        )

    @Test
    fun `required rejects blank`() {
        val result = Validators.validate(JsonPrimitive(""), listOf(rule(ValidationType.REQUIRED)))
        assertFalse(result.valid)
    }

    @Test
    fun `non-required rules pass on empty input so optional fields are not rejected`() {
        val result = Validators.validate(
            JsonPrimitive(""),
            listOf(rule(ValidationType.EMAIL), rule(ValidationType.MIN_LENGTH, 5)),
        )
        assertTrue(result.valid)
    }

    @Test
    fun `email format`() {
        assertTrue(Validators.validate(JsonPrimitive("a@b.co"), listOf(rule(ValidationType.EMAIL))).valid)
        assertFalse(Validators.validate(JsonPrimitive("a@b"), listOf(rule(ValidationType.EMAIL))).valid)
    }

    @Test
    fun `numeric bounds`() {
        val rules = listOf(rule(ValidationType.MIN, 10), rule(ValidationType.MAX, 100))
        assertTrue(Validators.validate(JsonPrimitive(50), rules).valid)
        assertFalse(Validators.validate(JsonPrimitive(5), rules).valid)
        assertFalse(Validators.validate(JsonPrimitive(500), rules).valid)
    }

    @Test
    fun `the first failing rule supplies the message`() {
        val result = Validators.validate(
            JsonPrimitive("ab"),
            listOf(
                rule(ValidationType.REQUIRED, message = "needed"),
                rule(ValidationType.MIN_LENGTH, 5, message = "too short"),
            ),
        )
        assertFalse(result.valid)
        assertEquals("too short", result.message)
    }

    @Test
    fun `matchesField compares against a sibling`() {
        val siblings = buildJsonObject { put("password", "secret123") }
        assertTrue(
            Validators.validate(
                JsonPrimitive("secret123"),
                listOf(rule(ValidationType.MATCHES_FIELD, "password")),
                siblings,
            ).valid,
        )
        assertFalse(
            Validators.validate(
                JsonPrimitive("nope"),
                listOf(rule(ValidationType.MATCHES_FIELD, "password")),
                siblings,
            ).valid,
        )
    }
}
