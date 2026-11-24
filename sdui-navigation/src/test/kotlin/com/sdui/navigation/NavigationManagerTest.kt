package com.sdui.navigation

import com.sdui.core.model.NavigationMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationManagerTest {

    private fun manager(root: String = "home") = NavigationManager().apply { setRoot(root) }

    @Test
    fun `setRoot installs a single entry`() {
        val nav = manager()
        assertEquals(1, nav.depth)
        assertEquals("home", nav.current!!.screenId)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `push and pop`() {
        val nav = manager()
        nav.navigate(NavigationRequest("detail", NavigationMode.PUSH))
        assertEquals(2, nav.depth)
        assertTrue(nav.canGoBack)
        assertTrue(nav.pop())
        assertEquals("home", nav.current!!.screenId)
    }

    @Test
    fun `popping the last entry reports exit instead of emptying the stack`() {
        val nav = manager()
        assertFalse("pop on a single entry must not succeed", nav.pop())
        assertEquals(1, nav.depth)
    }

    @Test
    fun `the same screen pushed twice gets independent entry ids`() {
        val nav = manager()
        nav.navigate(NavigationRequest("detail", NavigationMode.PUSH))
        val first = nav.current!!.entryId
        nav.navigate(NavigationRequest("detail", NavigationMode.PUSH))
        assertNotEquals(first, nav.current!!.entryId)
        assertEquals(3, nav.depth)
    }

    @Test
    fun `replace swaps the top without growing the stack`() {
        val nav = manager()
        nav.navigate(NavigationRequest("a", NavigationMode.PUSH))
        nav.navigate(NavigationRequest("b", NavigationMode.REPLACE))
        assertEquals(2, nav.depth)
        assertEquals("b", nav.current!!.screenId)
    }

    @Test
    fun `popToRoot collapses to the first entry`() {
        val nav = manager()
        repeat(4) { nav.navigate(NavigationRequest("s$it", NavigationMode.PUSH)) }
        assertTrue(nav.popToRoot())
        assertEquals(1, nav.depth)
        assertEquals("home", nav.current!!.screenId)
    }

    @Test
    fun `popTo unwinds to a named screen`() {
        val nav = manager()
        nav.navigate(NavigationRequest("cart", NavigationMode.PUSH))
        nav.navigate(NavigationRequest("checkout", NavigationMode.PUSH))
        nav.navigate(NavigationRequest("confirm", NavigationMode.PUSH))
        assertTrue(nav.popTo("cart"))
        assertEquals("cart", nav.current!!.screenId)
        assertEquals(2, nav.depth)
    }

    @Test
    fun `popTo a screen that is not on the stack is a no-op`() {
        val nav = manager()
        nav.navigate(NavigationRequest("a", NavigationMode.PUSH))
        assertFalse(nav.popTo("nowhere"))
        assertEquals(2, nav.depth)
    }

    @Test
    fun `overlays share the stack but are distinguishable`() {
        val nav = manager()
        nav.navigate(NavigationRequest("filters", NavigationMode.BOTTOM_SHEET))
        assertEquals(Presentation.BOTTOM_SHEET, nav.current!!.presentation)
        assertEquals("home", nav.currentScreen!!.screenId)
        assertTrue(nav.dismissOverlay())
        assertEquals("home", nav.current!!.screenId)
    }

    @Test
    fun `dismissOverlay does nothing when the top is a full screen`() {
        val nav = manager()
        nav.navigate(NavigationRequest("detail", NavigationMode.PUSH))
        assertFalse(nav.dismissOverlay())
        assertEquals(2, nav.depth)
    }

    @Test
    fun `pop delivers a result to the revealed entry, then clears it`() {
        val nav = manager()
        nav.navigate(NavigationRequest("expense", NavigationMode.PUSH))
        nav.pop(buildJsonObject { put("saved", true) })

        val revealed = nav.current!!
        assertEquals(JsonPrimitive(true), revealed.pendingResult!!["saved"])

        nav.consumeResult(revealed.entryId)
        assertNull(nav.current!!.pendingResult)
    }

    @Test
    fun `clearStack replaces everything below the new entry`() {
        val nav = manager()
        nav.navigate(NavigationRequest("a", NavigationMode.PUSH))
        nav.navigate(NavigationRequest("b", NavigationMode.PUSH, clearStack = true))
        assertEquals(1, nav.depth)
        assertEquals("b", nav.current!!.screenId)
    }

    @Test
    fun `navigate without a screen id fails for push but not for pop`() {
        val nav = manager()
        assertFalse(nav.navigate(NavigationRequest(null, NavigationMode.PUSH)))
        nav.navigate(NavigationRequest("a", NavigationMode.PUSH))
        assertTrue(nav.navigate(NavigationRequest(null, NavigationMode.POP)))
    }

    @Test
    fun `args ride along with the entry`() {
        val nav = manager()
        nav.navigate(
            NavigationRequest("detail", NavigationMode.PUSH, args = buildJsonObject { put("id", "t7") }),
        )
        assertEquals(JsonPrimitive("t7"), nav.current!!.args["id"])
    }
}
