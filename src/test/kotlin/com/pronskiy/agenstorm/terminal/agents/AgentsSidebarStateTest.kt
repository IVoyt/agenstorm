package com.pronskiy.agenstorm.terminal.agents

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Steps X1.3 and X6.3: the shared layout, view options included, survives a restart and changes only through copies. */
class AgentsSidebarStateTest {

    @Test
    fun storedInItsOwnFile() {
        val annotation = AgentsSidebarState::class.java.getAnnotation(State::class.java)

        assertEquals(listOf("agenstorm-agents.xml"), annotation.storages.map(Storage::value))
    }

    @Test
    fun aLayoutRoundTripsThroughXml() {
        val layout = AgentsSidebarState.Layout(visible = true, width = 576, anchor = "right", selected = "group:/w/app", collapsed = mutableListOf("/w/a", "/w/b"), scroll = 12)

        val element = XmlSerializer.serialize(layout, SkipDefaultsSerializationFilter())

        assertEquals(layout, XmlSerializer.deserialize(element, AgentsSidebarState.Layout::class.java))
    }

    @Test
    fun theViewOptionsRoundTripAndStartAsBefore() {
        val layout = AgentsSidebarState.Layout(show = AgentsSidebarState.Shown.ACTIVE, groupBy = AgentsSidebarState.Grouping.STATUS)

        val element = XmlSerializer.serialize(layout, SkipDefaultsSerializationFilter())

        assertEquals(layout, XmlSerializer.deserialize(element, AgentsSidebarState.Layout::class.java))
        assertEquals(AgentsSidebarState.Shown.ALL, AgentsSidebarState.Layout().show)
        assertEquals(AgentsSidebarState.Grouping.PROJECT, AgentsSidebarState.Layout().groupBy)
    }

    @Test
    fun aLayoutSavedBeforeTheViewOptionsLoadsWithTodaysView() {
        val saved = JDOMUtil.load("""<Layout><option name="visible" value="true" /><option name="width" value="320" /></Layout>""")

        val layout = XmlSerializer.deserialize(saved, AgentsSidebarState.Layout::class.java)

        assertEquals(320, layout.width)
        assertEquals(AgentsSidebarState.Shown.ALL, layout.show)
        assertEquals(AgentsSidebarState.Grouping.PROJECT, layout.groupBy)
    }

    @Test
    fun updateHandsOutCopiesAndReportsAChange() {
        val state = AgentsSidebarState()
        val before = state.layout.value

        assertTrue(state.update { it.apply { collapsed.add("/w/a"); width = 300 } })
        assertEquals(emptyList<String>(), before.collapsed)
        assertEquals(listOf("/w/a"), state.layout.value.collapsed)
        assertEquals(300, state.layout.value.width)

        assertFalse("the same layout again is no change", state.update { it })
    }

    @Test
    fun aLoadedLayoutIsNotSharedWithTheCaller() {
        val state = AgentsSidebarState()
        val loaded = AgentsSidebarState.Layout(collapsed = mutableListOf("/w/a"))

        state.loadState(loaded)
        loaded.collapsed.add("/w/b")

        assertEquals(listOf("/w/a"), state.layout.value.collapsed)
    }
}
