package com.pronskiy.agenstorm.terminal.agents

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.pronskiy.agenstorm.terminal.agents.AgentsSidebarState.Grouping
import com.pronskiy.agenstorm.terminal.agents.AgentsSidebarState.Shown

/** Step X6.3: the title bar's View Options, a choice of what to show and how to group it, kept in the shared layout. */
class AgentsViewOptionsTest : BasePlatformTestCase() {

    private val state get() = AgentsSidebarState.getInstance()
    private val group = AgentsViewOptions()

    override fun setUp() {
        super.setUp()
        state.loadState(AgentsSidebarState.Layout())
    }

    override fun tearDown() {
        try {
            state.loadState(AgentsSidebarState.Layout())
        } finally {
            super.tearDown()
        }
    }

    private fun children(): List<AnAction> = group.getChildren(TestActionEvent.createTestEvent(group)).toList()

    private fun option(text: String): ToggleAction = children().filterIsInstance<ToggleAction>().single { it.templatePresentation.text == text }

    private fun selected(text: String): Boolean = option(text).let { it.isSelected(TestActionEvent.createTestEvent(it)) }

    private fun choose(text: String) = option(text).let { it.setSelected(TestActionEvent.createTestEvent(it), true) }

    fun testAPopupOfTwoChoices() {
        assertTrue((group as ActionGroup).isPopup)
        assertEquals("View Options", group.templatePresentation.text)
        assertEquals(
            listOf("Show", "Active Sessions", "All Sessions", "Group By", "Project", "Status"),
            children().map { (it as? Separator)?.text ?: it.templatePresentation.text },
        )
    }

    fun testTodaysViewIsSelectedAtFirst() {
        assertTrue(selected("All Sessions"))
        assertFalse(selected("Active Sessions"))
        assertTrue(selected("Project"))
        assertFalse(selected("Status"))
    }

    fun testAChoiceGoesIntoTheSharedLayout() {
        choose("Active Sessions")
        choose("Status")

        assertEquals(Shown.ACTIVE, state.layout.value.show)
        assertEquals(Grouping.STATUS, state.layout.value.groupBy)
        assertTrue(selected("Active Sessions"))
        assertFalse(selected("All Sessions"))

        choose("All Sessions")
        choose("Project")
        assertEquals(AgentsSidebarState.Layout(), state.layout.value)
    }

    fun testChoosingTheSelectedOneAgainKeepsIt() {
        option("All Sessions").setSelected(TestActionEvent.createTestEvent(option("All Sessions")), false)

        assertEquals("a radio choice cannot be unticked", Shown.ALL, state.layout.value.show)
    }
}
